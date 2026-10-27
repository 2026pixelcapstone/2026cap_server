package com.expansion.server.domain.ai.client;

import com.expansion.server.domain.ai.dto.ShadeSuggestResponse;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 명암 제안 — 자체 모델 내부 파이썬 서비스(ml/serve, 서버 안 127.0.0.1:8090) 호출 클라이언트.
 *
 * <p>r2.enabled 패턴과 동일: {@code shade.enabled=false}면 호출하지 않고 503(SHADE_UNAVAILABLE).
 * 목킹 응답은 두지 않는다 — 로컬에서도 파이썬 서비스를 띄워 개발한다(ml/serve/README.md).
 * 파이썬 서비스는 로그인이 없으므로 바깥(터널·방화벽)에 열지 않고, 로그인·입력 검사·연타 방지는 여기(스프링)가 맡는다.
 */
@Slf4j
@Component
public class ShadeModelClient {

    // 장당 계산은 수 ms(이 PC CPU 8ms) — 짧게 잡아 파이썬이 멈춰도 요청 스레드를 오래 잡지 않게
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;

    public ShadeModelClient(
            @Value("${shade.enabled:false}") boolean enabled,
            @Value("${shade.base-url:http://127.0.0.1:8090}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * @param imageBase64 순수 base64 PNG(접두어 제거·크기 검사 끝난 것)
     * @param angle       광원 각도(°)
     */
    public ShadeSuggestResponse suggest(String imageBase64, double angle) {
        if (!enabled) {
            throw new CustomException(ErrorCode.SHADE_UNAVAILABLE);
        }
        try {
            ShadeSuggestResponse res = restClient.post()
                    .uri(baseUrl + "/shade")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("image", imageBase64, "angle", angle))
                    .retrieve()
                    .body(ShadeSuggestResponse.class);
            if (res == null) {
                throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
            }
            return res;
        } catch (CustomException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            // 앞단(스프링) 검사를 빠져나온 입력 — 파이썬이 400·413으로 거절
            log.warn("[AI] 명암 서비스가 입력을 거절: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.INVALID_INPUT, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 파이썬 꺼짐·타임아웃·5xx
            log.error("[AI] 명암 서비스 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SHADE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("[AI] 명암 서비스 응답 처리 실패", e);
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED, e);
        }
    }
}
