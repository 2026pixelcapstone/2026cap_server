package com.expansion.server.domain.ai.service;

import com.expansion.server.domain.ai.client.GeminiClient;
import com.expansion.server.domain.ai.dto.PaletteSuggestRequest;
import com.expansion.server.domain.ai.dto.PaletteSuggestResponse;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * AI 색 팔레트 추천 서비스.
 *
 * <p>DB를 건드리지 않는다(즉석 추천, 저장 없음) → 트랜잭션 불필요. 입력 검증 후 {@link GeminiClient}에 위임.
 * 잘못된 이미지(비 base64·비 PNG)는 외부 API로 넘기기 전에 400(INVALID_INPUT)으로 걸러 502를 방지한다.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    /** 요청 base64 상한(대략 6MB 이미지). 픽셀아트는 훨씬 작지만 과대 요청 방어. */
    private static final int MAX_IMAGE_BASE64_LENGTH = 8_000_000;

    private final GeminiClient geminiClient;

    public PaletteSuggestResponse suggestPalette(Long userId, PaletteSuggestRequest req) {
        String image = stripDataUrlPrefix(req.imageBase64());
        if (image.isBlank() || image.length() > MAX_IMAGE_BASE64_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        validatePngImage(image);   // base64 디코드 + PNG 시그니처 확인(외부 API 전 차단)

        List<String> currentColors = req.currentColors() != null ? req.currentColors() : List.of();
        List<String> colors = geminiClient.suggestColors(image, currentColors, req.description());
        return new PaletteSuggestResponse(colors);
    }

    /** "data:image/png;base64,...." 접두어가 있으면 순수 base64만 남긴다. */
    private String stripDataUrlPrefix(String s) {
        int comma = s.indexOf(',');
        return (s.startsWith("data:") && comma > 0) ? s.substring(comma + 1) : s;
    }

    /** base64로 디코드되며 PNG 시그니처(89 50 4E 47 = ‰PNG)로 시작하는지 검증. 아니면 INVALID_INPUT. */
    private void validatePngImage(String base64) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (decoded.length < 8
                || (decoded[0] & 0xFF) != 0x89
                || decoded[1] != 'P' || decoded[2] != 'N' || decoded[3] != 'G') {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
