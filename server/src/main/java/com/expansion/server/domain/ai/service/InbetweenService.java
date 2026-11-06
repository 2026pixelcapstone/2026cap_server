package com.expansion.server.domain.ai.service;

import com.expansion.server.domain.ai.client.ShadeModelClient;
import com.expansion.server.domain.ai.dto.InbetweenRequest;
import com.expansion.server.domain.ai.dto.InbetweenResponse;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;

/**
 * 중간 동작 제안 — 키프레임 두 장을 검사해 내부 파이썬 서비스(ml/serve /inbetween)로 넘김.
 *
 * <p>검사(파이썬에 닿기 전): base64·PNG 머리, 두 장 모두 128×128 이하(그림을 풀기 전 머리에서 크기만 읽음), 같은 캔버스 크기,
 * 사용자당 5초에 한 번(파이썬은 한 번에 하나씩 계산). 그림 부분 60칸 초과 등 내용 검사는 파이썬(400 → INVALID_INPUT).
 */
@Service
public class InbetweenService {

    static final int MAX_SIDE = 128;
    static final int FRAME_COUNT = 9;     // t = 0.1 … 0.9
    private static final Duration COOLDOWN = Duration.ofSeconds(5);

    private final ShadeModelClient client;
    private final Cache<Long, Boolean> recent = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(COOLDOWN)
            .build();

    public InbetweenService(ShadeModelClient client) {
        this.client = client;
    }

    public InbetweenResponse suggest(Long userId, InbetweenRequest req) {
        String a = strip(req.imageA());
        String c = strip(req.imageC());
        int[] sa = pngSize(a);
        int[] sc = pngSize(c);
        if (sa[0] != sc[0] || sa[1] != sc[1]) {
            throw new CustomException(ErrorCode.INBETWEEN_SIZE_MISMATCH);
        }
        if (recent.asMap().putIfAbsent(userId, Boolean.TRUE) != null) {
            throw new CustomException(ErrorCode.INBETWEEN_TOO_MANY_REQUESTS);
        }
        InbetweenResponse res = client.inbetween(a, c);
        // 응답 검사 — 모양이 어긋나면 화면에 이상한 프레임을 넣지 않게 502
        if (res.frames() == null || res.frames().size() != FRAME_COUNT
                || res.frames().stream().anyMatch(f -> f == null || f.t() == null || f.image() == null || f.image().isBlank())
                || res.parts() == null || res.joints() == null) {
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
        }
        return res;
    }

    private String strip(String s) {
        int comma = s.indexOf(',');
        return (s.startsWith("data:") && comma > 0) ? s.substring(comma + 1) : s;
    }

    /** PNG 머리에서 크기만 읽음(픽셀은 풀지 않음) */
    private int[] pngSize(String base64) {
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
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, true, true);
            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            if (w > MAX_SIDE || h > MAX_SIDE) {
                throw new CustomException(ErrorCode.INBETWEEN_IMAGE_TOO_LARGE);
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
}
