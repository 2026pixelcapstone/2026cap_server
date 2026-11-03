package com.expansion.server.domain.ai.service;

import com.expansion.server.domain.ai.client.ShadeModelClient;
import com.expansion.server.domain.ai.dto.ShadeSuggestRequest;
import com.expansion.server.domain.ai.dto.ShadeSuggestResponse;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 명암 제안(A-3) — 자체 모델. 입력 검사 → 사용자별 연타 방지 → 내부 파이썬 서비스 → 응답 검증.
 *
 * <p>DB를 건드리지 않는다(즉석 제안, 저장 없음). 큰 그림 폭탄 방어는 겹으로:
 * 본문 상한(ShadeBodyLimitFilter) → base64 길이(DTO) → PNG 머리만 읽어 128×128 초과면 픽셀을 풀기 전에 거절
 * → 색 수·같은 색 덩어리 수 상한(계산 폭탄 — 칸마다 색이 다른 잡음은 파이썬에서 13초) → 파이썬 서비스가 같은 검사를 한 번 더
 * (ml/serve/README.md).
 */
@Service
public class ShadeSuggestService {

    static final int MAX_SIDE = 128;          // v2 — 64 이하는 64 캔버스, 그 이상은 128 캔버스(파이썬이 정함)
    static final int MIN_OPAQUE = 100;        // 명암 기준서 7절 — 작은 그림은 대상 아님
    /** 파이썬(ml/serve/shade.py)과 같은 상한. 여기서는 외곽선까지 세므로 같은 숫자여도 조금 더 엄격(실제 그림은 최대 24색·덩어리 608개) */
    static final int MAX_COLORS = 128;
    static final int MAX_PIECES = 1500;
    private static final double DEFAULT_ANGLE = 135.0;   // PixelPilot 기본 광원 = 왼쪽 위
    private static final Duration COOLDOWN = Duration.ofSeconds(5);
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final List<String> RAMP_KEYS = List.of("-2", "-1", "0", "1", "2");

    private final ShadeModelClient client;
    /** 사용자별 마지막 요청(5초 뒤 자동 삭제). 서버 한 대 전제 — 채팅 presence와 같음. */
    private final Cache<Long, Boolean> recent = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(COOLDOWN)
            .build();

    public ShadeSuggestService(ShadeModelClient client) {
        this.client = client;
    }

    public ShadeSuggestResponse suggest(Long userId, ShadeSuggestRequest req) {
        String image = stripDataUrlPrefix(req.imageBase64());
        int[] size = checkImage(image);
        double angle = req.angle() != null ? req.angle() : DEFAULT_ANGLE;

        // 검사를 통과한 요청만 파이썬(한 번에 하나씩 계산)으로 — 한 사람이 줄을 차지하지 않게 5초에 한 번
        if (recent.asMap().putIfAbsent(userId, Boolean.TRUE) != null) {
            throw new CustomException(ErrorCode.SHADE_TOO_MANY_REQUESTS);
        }
        ShadeSuggestResponse res = client.suggest(image, angle);
        validateResponse(res, size[0], size[1]);
        validateStrengths(res, size[0], size[1]);
        return res;
    }

    private String stripDataUrlPrefix(String s) {
        int comma = s.indexOf(',');
        return (s.startsWith("data:") && comma > 0) ? s.substring(comma + 1) : s;
    }

    /** base64 → PNG 머리만 읽어 크기 검사(128×128 초과면 픽셀을 풀지 않고 거절) → 불투명 칸 수·색 수·덩어리 수. 반환 {가로, 세로} */
    private int[] checkImage(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT, e);
        }
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
        }
        ImageReader reader = readers.next();
        // 메모리에서만 읽음(ImageIO 기본값은 요청마다 디스크 임시 파일을 쓸 수 있음)
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, true, true);       // 앞으로만 읽기 · 메타데이터(글자 덩어리 등) 무시
            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            if (w > MAX_SIDE || h > MAX_SIDE) {
                throw new CustomException(ErrorCode.SHADE_IMAGE_TOO_LARGE);
            }
            BufferedImage img = reader.read(0);   // 128×128 이하만 여기까지 옴
            int[] rgb = new int[w * h];           // 불투명 칸은 RGB, 투명은 -1
            int opaque = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    if ((argb >>> 24) != 0) {
                        rgb[y * w + x] = argb & 0xFFFFFF;
                        opaque++;
                    } else {
                        rgb[y * w + x] = -1;
                    }
                }
            }
            if (opaque < MIN_OPAQUE) {
                throw new CustomException(ErrorCode.SHADE_IMAGE_TOO_SMALL);
            }
            if (Arrays.stream(rgb).filter(v -> v >= 0).distinct().count() > MAX_COLORS
                    || countPieces(rgb, w, h) > MAX_PIECES) {
                throw new CustomException(ErrorCode.SHADE_IMAGE_TOO_COMPLEX);
            }
            return new int[]{w, h};
        } catch (CustomException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT, e);
        } finally {
            reader.dispose();
        }
    }

    /** 같은 색 4방향 연결 덩어리 수(합집합-찾기, 칸 수에 비례) */
    static int countPieces(int[] rgb, int w, int h) {
        int[] parent = new int[w * h];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (rgb[i] < 0) {
                    continue;
                }
                if (x + 1 < w && rgb[i + 1] == rgb[i]) {
                    union(parent, i, i + 1);
                }
                if (y + 1 < h && rgb[i + w] == rgb[i]) {
                    union(parent, i, i + w);
                }
            }
        }
        int pieces = 0;
        for (int i = 0; i < parent.length; i++) {
            if (rgb[i] >= 0 && find(parent, i) == i) {
                pieces++;
            }
        }
        return pieces;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    /** 파이썬 응답도 믿지 않는다 — 크기·단계 범위·색 형식이 어긋나면 화면이 깨지므로 502 */
    private void validateResponse(ShadeSuggestResponse res, int w, int h) {
        boolean ok = res.width() == w && res.height() == h
                && res.levels() != null && res.levels().size() == h
                && res.materials() != null;
        if (ok) {
            for (List<Integer> row : res.levels()) {
                if (row == null || row.size() != w
                        || row.stream().anyMatch(v -> v != null && (v < -2 || v > 2))) {
                    ok = false;
                    break;
                }
            }
        }
        if (ok) {
            for (ShadeSuggestResponse.Material m : res.materials()) {
                Map<String, String> ramp = m.ramp();
                if (m.base() == null || !HEX.matcher(m.base()).matches() || ramp == null
                        || !RAMP_KEYS.stream().allMatch(k -> ramp.get(k) != null && HEX.matcher(ramp.get(k)).matches())) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
        }
    }

    /**
     * 강도별 결과(levelsByStrength)도 검증 — 강도 목록·기본 강도가 있고, 강도마다 길이 w×h 문자열('0'~'4', '.')이며
     * '.'(대상 아님) 자리가 levels의 null 자리와 같아야 한다. 키는 파이썬 %g 형식(0.3 → "0.3", 1.0 → "1").
     */
    private void validateStrengths(ShadeSuggestResponse res, int w, int h) {
        List<Double> strengths = res.strengths();
        Map<String, String> by = res.levelsByStrength();
        boolean ok = strengths != null && !strengths.isEmpty() && strengths.size() <= 8 && by != null
                && res.defaultStrength() != null && strengths.contains(res.defaultStrength());
        if (ok) {
            for (Double k : strengths) {
                String s = k == null ? null : by.get(strengthKey(k));
                if (s == null || s.length() != w * h) {
                    ok = false;
                    break;
                }
                for (int i = 0; i < s.length() && ok; i++) {
                    char c = s.charAt(i);
                    boolean none = res.levels().get(i / w).get(i % w) == null;
                    ok = none ? c == '.' : (c >= '0' && c <= '4');
                }
                if (!ok) {
                    break;
                }
            }
        }
        if (!ok) {
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
        }
    }

    static String strengthKey(double k) {
        return BigDecimal.valueOf(k).stripTrailingZeros().toPlainString();
    }
}
