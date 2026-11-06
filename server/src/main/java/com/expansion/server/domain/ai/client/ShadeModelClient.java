package com.expansion.server.domain.ai.client;

import com.expansion.server.domain.ai.dto.InbetweenResponse;
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
import java.util.Set;

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

    // v2 장당 계산 약 0.1~0.4초(이 PC, 최악 상한 안쪽 약 0.7초), 파이썬은 한 번에 하나씩 → 앞사람 대기까지 넉넉히 10초.
    // 파이썬이 멈춰도 요청 스레드를 오래 잡지 않게 이 이상은 늘리지 않음
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    /** 파이썬 서비스가 '입력이 잘못됨'으로 쓰는 상태(400·413, 422는 FastAPI 기본값이라 함께) */
    private static final Set<Integer> INPUT_REJECTED = Set.of(400, 413, 422);

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;
    /** 중간 동작 제안 — 처음 한 번 이해 모델을 불러오느라 서버 CPU에서 수 초 걸릴 수 있어 읽기 시한만 30초 */
    private final RestClient inbetweenClient;

    public ShadeModelClient(
            @Value("${shade.enabled:false}") boolean enabled,
            @Value("${shade.base-url:http://127.0.0.1:8090}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        SimpleClientHttpRequestFactory slow = new SimpleClientHttpRequestFactory();
        slow.setConnectTimeout(CONNECT_TIMEOUT);
        slow.setReadTimeout(Duration.ofSeconds(30));
        this.inbetweenClient = RestClient.builder().requestFactory(slow).build();
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
            int status = e.getStatusCode().value();
            if (INPUT_REJECTED.contains(status)) {
                // 앞단(스프링) 검사를 빠져나온 입력 — 파이썬 계약상 입력 거절(ml/serve/README.md)
                log.warn("[AI] 명암 서비스가 입력을 거절: {} {}", status, e.getResponseBodyAsString());
                throw new CustomException(ErrorCode.INVALID_INPUT, e);
            }
            // 404 등 — 입력이 아니라 설정(base-url)·연동 문제
            log.error("[AI] 명암 서비스 연동 오류: {} {}", status, e.getMessage());
            throw new CustomException(ErrorCode.SHADE_UNAVAILABLE, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 파이썬 꺼짐·타임아웃·5xx
            log.error("[AI] 명암 서비스 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SHADE_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("[AI] 명암 서비스 응답 처리 실패", e);
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED, e);
        }
    }

    /** 중간 동작 제안(같은 파이썬 서비스의 /inbetween). 오류 처리는 명암과 같은 규칙 */
    public InbetweenResponse inbetween(String imageA, String imageC) {
        if (!enabled) {
            throw new CustomException(ErrorCode.INBETWEEN_UNAVAILABLE);
        }
        try {
            InbetweenResponse res = inbetweenClient.post()
                    .uri(baseUrl + "/inbetween")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("imageA", imageA, "imageC", imageC))
                    .retrieve()
                    .body(InbetweenResponse.class);
            if (res == null) {
                throw new CustomException(ErrorCode.AI_SUGGEST_FAILED);
            }
            return res;
        } catch (CustomException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            if (INPUT_REJECTED.contains(status)) {
                log.warn("[AI] 중간 동작 서비스가 입력을 거절: {} {}", status, e.getResponseBodyAsString());
                throw new CustomException(ErrorCode.INVALID_INPUT, e);
            }
            log.error("[AI] 중간 동작 서비스 연동 오류: {} {}", status, e.getMessage());
            throw new CustomException(ErrorCode.INBETWEEN_UNAVAILABLE, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("[AI] 중간 동작 서비스 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INBETWEEN_UNAVAILABLE, e);
        } catch (Exception e) {
            log.error("[AI] 중간 동작 서비스 응답 처리 실패", e);
            throw new CustomException(ErrorCode.AI_SUGGEST_FAILED, e);
        }
    }
}
