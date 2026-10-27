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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 명암 제안(A-3) — 자체 모델. 입력 검사 → 사용자별 연타 방지 → 내부 파이썬 서비스 → 응답 검증.
 *
 * <p>DB를 건드리지 않는다(즉석 제안, 저장 없음). 큰 그림 폭탄 방어는 겹으로:
 * 본문 상한(ShadeBodyLimitFilter) → base64 길이(DTO) → PNG 머리만 읽어 64×64 초과면 픽셀을 풀기 전에 거절
 * → 파이썬 서비스가 같은 검사를 한 번 더(ml/serve/README.md).
 */
@Service
public class ShadeSuggestService {

    static final int MAX_SIDE = 64;           // 모델 캔버스
    static final int MIN_OPAQUE = 100;        // 명암 기준서 7절 — 작은 그림은 대상 아님
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
        return res;
    }

    private String stripDataUrlPrefix(String s) {
        int comma = s.indexOf(',');
        return (s.startsWith("data:") && comma > 0) ? s.substring(comma + 1) : s;
    }

    /** base64 → PNG 머리만 읽어 크기 검사(64×64 초과면 픽셀을 풀지 않고 거절) → 불투명 칸 수. 반환 {가로, 세로} */
    private int[] checkImage(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, true, true);       // 앞으로만 읽기 · 메타데이터(글자 덩어리 등) 무시
            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            if (w > MAX_SIDE || h > MAX_SIDE) {
                throw new CustomException(ErrorCode.SHADE_IMAGE_TOO_LARGE);
            }
            BufferedImage img = reader.read(0);   // 64×64 이하만 여기까지 옴
            int opaque = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((img.getRGB(x, y) >>> 24) != 0) {
                        opaque++;
                    }
                }
            }
            if (opaque < MIN_OPAQUE) {
                throw new CustomException(ErrorCode.SHADE_IMAGE_TOO_SMALL);
            }
            return new int[]{w, h};
        } catch (IOException | RuntimeException e) {
            if (e instanceof CustomException ce) {
                throw ce;
            }
            throw new CustomException(ErrorCode.INVALID_INPUT, e);
        } finally {
            reader.dispose();
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
}
