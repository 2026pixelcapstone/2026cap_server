package com.expansion.server.domain.ai.client;

import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemini 멀티모달 API 호출 클라이언트 (색 팔레트 추천).
 *
 * <p>toss.enabled / r2.enabled 패턴과 동일: {@code gemini.enabled=false}(로컬 기본)이면 실제 API를
 * 호출하지 않고 <b>목킹 팔레트</b>를 반환한다. 키 발급 후 {@code GEMINI_ENABLED=true}+{@code GEMINI_API_KEY}를
 * 주입하면 실제 Gemini {@code generateContent}를 호출한다.
 *
 * <p>⚠️ 실호출 경로(callGemini)는 키 발급 후 실제 응답으로 최종 검증·조정 예정. 현재 구조/목킹 단계.
 */
@Slf4j
@Component
public class GeminiClient {

    /** 추천 색 개수(7~8개 목표). */
    private static final int PALETTE_SIZE = 8;

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public GeminiClient(
            @Value("${gemini.enabled:false}") boolean enabled,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-flash-lite-latest}") String model,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * 작업물 이미지 + 현재 색 + 자연어 설명을 근거로 어울리는 색 팔레트를 추천한다.
     *
     * @param imageBase64   data URL 접두어가 제거된 순수 base64 PNG
     * @param currentColors 현재 사용된 색(hex), 없으면 빈 리스트
     * @param description   원하는 느낌/컨셉(선택, null 허용)
     * @return 추천 색(hex) 목록
     */
    public List<String> suggestColors(String imageBase64, List<String> currentColors, String description) {
        if (!enabled) {
            log.info("[AI] gemini.enabled=false → 목킹 팔레트 반환");
            return mockPalette();
        }
        return callGemini(imageBase64, currentColors, description);
    }

    // ── 목킹: 키 없이 구조·플로우 검증용 고정 팔레트 ──────────────────
    private List<String> mockPalette() {
        return List.of(
                "#2D5A3D", "#3E8E5A", "#A7D7A0", "#F2E9C4",
                "#E9A23B", "#C7572B", "#5B3A29", "#1A1A2E"
        );
    }

    // ── 실호출: Gemini generateContent (이미지 + 프롬프트 → JSON) ──────
    private List<String> callGemini(String imageBase64, List<String> currentColors, String description) {
        try {
            Map<String, Object> body = buildRequestBody(imageBase64, currentColors, description);
            String url = baseUrl + "/v1beta/models/" + model + ":generateContent";

            Map<?, ?> resp = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return parseColors(resp);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AI] Gemini 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED, e);
        }
    }

    /** Gemini 요청 바디: 이미지 파트 + 텍스트 프롬프트 + JSON 스키마 강제. */
    private Map<String, Object> buildRequestBody(String imageBase64, List<String> currentColors, String description) {
        Map<String, Object> textPart = Map.of("text", buildPrompt(currentColors, description));
        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of("mime_type", "image/png", "data", imageBase64));
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));

        // response_schema로 { "colors": ["#...", ...] } 형태를 강제(유효 JSON 보장)
        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of("colors", Map.of(
                        "type", "ARRAY",
                        "items", Map.of("type", "STRING"))),
                "required", List.of("colors"));
        Map<String, Object> generationConfig = Map.of(
                "response_mime_type", "application/json",
                "response_schema", schema);

        return Map.of("contents", List.of(content), "generationConfig", generationConfig);
    }

    private String buildPrompt(List<String> currentColors, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a color palette assistant for pixel art. ")
          .append("Look at the image and suggest exactly ").append(PALETTE_SIZE)
          .append(" harmonious colors that complement the artwork (an extended palette). ");
        if (currentColors != null && !currentColors.isEmpty()) {
            sb.append("Colors already used: ").append(String.join(", ", currentColors)).append(". ");
        }
        if (description != null && !description.isBlank()) {
            sb.append("Desired mood/concept: ").append(description).append(". ");
        }
        sb.append("Respond with hex colors in #RRGGBB format only.");
        return sb.toString();
    }

    /** Gemini 응답에서 colors 배열 추출: candidates[0].content.parts[0].text(=JSON) → colors[]. */
    @SuppressWarnings("unchecked")
    private List<String> parseColors(Map<?, ?> resp) {
        try {
            List<?> candidates = (List<?>) resp.get("candidates");
            Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            List<?> parts = (List<?>) content.get("parts");
            String text = (String) ((Map<?, ?>) parts.get(0)).get("text");

            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
            List<String> colors = new ArrayList<>((List<String>) parsed.get("colors"));
            if (colors.isEmpty()) {
                throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
            }
            return colors;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AI] Gemini 응답 파싱 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED, e);
        }
    }
}
