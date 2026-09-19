package com.expansion.server.domain.ai.client;

import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Gemini 멀티모달 API 호출 클라이언트 (색 팔레트 추천).
 *
 * <p>toss.enabled / r2.enabled 패턴과 동일: {@code gemini.enabled=false}(로컬 기본)이면 실제 API를
 * 호출하지 않고 <b>목킹 팔레트</b>를 반환한다. 키 발급 후 {@code GEMINI_ENABLED=true}+{@code GEMINI_API_KEY}를
 * 주입하면 실제 Gemini {@code generateContent}를 호출한다.
 *
 * <p>기능1(이미지 기반)·기능2(태그 기반)가 요청 파트만 다르고, 전송/파싱/스키마는 공유한다({@link #execute}).
 */
@Slf4j
@Component
public class GeminiClient {

    /** 추천 색 목표 개수(프롬프트·스키마로 요청). */
    private static final int PALETTE_SIZE = 8;
    /** 파싱 후 유효 hex 최소 개수 — LLM이 살짝 어긋나도(7개 등) 기능이 돌도록 유연하게 통과. */
    private static final int MIN_PALETTE_SIZE = 4;
    /** 유효 색상 형식(#RRGGBB). */
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

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
        // 외부 API 지연이 요청 스레드를 무한 점유하지 않도록 연결/응답 타임아웃을 명시한다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    // ── 기능1: 내 작업물 색 추천 (이미지 + 현재색 + 자연어) ──────────
    public List<String> suggestColors(String imageBase64, List<String> currentColors, String description) {
        if (!enabled) {
            log.info("[AI] gemini.enabled=false → 목킹 팔레트 반환(image)");
            return mockPalette();
        }
        Map<String, Object> textPart = Map.of("text", buildImagePrompt(currentColors, description));
        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of("mime_type", "image/png", "data", imageBase64));
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        return execute(content);
    }

    // ── 기능2: 태그로 색 찾기 (텍스트만, 이미지 없음) ─────────────────
    public List<String> suggestColorsByTags(List<String> tags) {
        if (!enabled) {
            log.info("[AI] gemini.enabled=false → 목킹 팔레트 반환(tags)");
            return mockPalette();
        }
        Map<String, Object> textPart = Map.of("text", buildTagPrompt(tags));
        Map<String, Object> content = Map.of("parts", List.of(textPart));
        return execute(content);
    }

    // ── 목킹: 키 없이 구조·플로우 검증용 고정 팔레트 ──────────────────
    private List<String> mockPalette() {
        return List.of(
                "#2D5A3D", "#3E8E5A", "#A7D7A0", "#F2E9C4",
                "#E9A23B", "#C7572B", "#5B3A29", "#1A1A2E"
        );
    }

    // ── 공통 전송 + 파싱 (기능1·2 공유) ─────────────────────────────
    private List<String> execute(Map<String, Object> content) {
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(content),
                    "generationConfig", generationConfig());
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

    /** response_schema로 { "colors": [정확히 8개] } 형태를 강제(유효 JSON 유도). */
    private Map<String, Object> generationConfig() {
        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of("colors", Map.of(
                        "type", "ARRAY",
                        "minItems", PALETTE_SIZE,
                        "maxItems", PALETTE_SIZE,
                        "items", Map.of("type", "STRING"))),
                "required", List.of("colors"));
        return Map.of(
                "response_mime_type", "application/json",
                "response_schema", schema);
    }

    private String buildImagePrompt(List<String> currentColors, String description) {
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

    private String buildTagPrompt(List<String> tags) {
        return "You are a color palette assistant for pixel art. "
                + "Suggest exactly " + PALETTE_SIZE
                + " harmonious colors that fit these concepts/tags: " + String.join(", ", tags) + ". "
                + "Respond with hex colors in #RRGGBB format only.";
    }

    /**
     * Gemini 응답에서 colors 추출: candidates[0].content.parts[0].text(=JSON) → colors[].
     * 유효 hex(#RRGGBB)만 추리고, 최소 개수 미만이면 실패, 초과분은 목표 개수까지만 사용(완화 정책).
     */
    private List<String> parseColors(Map<?, ?> resp) {
        try {
            List<?> candidates = (List<?>) resp.get("candidates");
            Map<?, ?> content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            List<?> parts = (List<?>) content.get("parts");
            String text = (String) ((Map<?, ?>) parts.get(0)).get("text");

            Map<?, ?> parsed = objectMapper.readValue(text, Map.class);
            Object rawColors = parsed.get("colors");
            if (!(rawColors instanceof List<?> list)) {
                throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
            }

            List<String> colors = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof String s && HEX.matcher(s.trim()).matches()) {
                    colors.add(s.trim().toUpperCase());
                }
            }
            if (colors.size() < MIN_PALETTE_SIZE) {
                throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
            }
            return colors.size() > PALETTE_SIZE
                    ? new ArrayList<>(colors.subList(0, PALETTE_SIZE))
                    : colors;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AI] Gemini 응답 파싱 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED, e);
        }
    }
}
