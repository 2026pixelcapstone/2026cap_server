package com.expansion.server.domain.payment.client;

import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 코어 API 호출(승인/취소). Basic 인증 = base64(secretKey + ":").
 *
 * toss.enabled=false(로컬 기본): 실제 카드 청구가 불가능하고 키도 없으므로 HTTP를 건너뛰고
 *   시뮬레이션 결과를 돌려준다(mail.enabled / r2.enabled 로컬 목킹 패턴과 동일). 상태 전이·게이트
 *   로직을 로컬 curl로 검증하기 위한 것. 실제 승인 왕복은 프로덕션(또는 샌드박스 키)에서만.
 * toss.enabled=true(프로덕션): 실제 토스 API 호출.
 */
@Slf4j
@Component
public class TossPaymentClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String CANCEL_PATH  = "/v1/payments/%s/cancel";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final boolean enabled;
    private final String secretKey;
    private final RestClient restClient;

    public TossPaymentClient(
            @Value("${toss.enabled:false}") boolean enabled,
            @Value("${toss.secret-key:}") String secretKey,
            @Value("${toss.base-url:https://api.tosspayments.com}") String baseUrl) {
        // 활성화됐는데 시크릿 키가 없으면 조용히 실패하지 않고 기동 시점에 알린다.
        if (enabled && (secretKey == null || secretKey.isBlank())) {
            throw new IllegalStateException("toss.enabled=true 인데 toss.secret-key 가 비어 있습니다.");
        }
        this.enabled = enabled;
        this.secretKey = secretKey;

        // 외부 API가 느리거나 멈춰도 요청 스레드가 무한 대기하지 않도록 타임아웃 지정.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private String authHeader() {
        String raw = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 결제 승인. 성공 시 실제 카드가 청구된다. paymentKey/method를 담은 결과 반환. */
    public TossConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount) {
        long krw = toWon(amount);   // 소수 금액이면 여기서 400으로 걸러짐(잘못된 데이터 방어)
        if (!enabled) {
            // 로컬 목킹: 실제 청구 없이 성공 처리(상태 전이 검증용).
            log.info("[TOSS-MOCK] confirm skipped (toss.enabled=false) orderId={} amount={}", orderId, krw);
            return new TossConfirmResult(paymentKey, "간편결제", "DONE");
        }
        try {
            Map<?, ?> res = restClient.post()
                    .uri(CONFIRM_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    // Idempotency-Key: 같은 orderId 재승인 시 중복 청구 방지
                    .header("Idempotency-Key", orderId)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", krw))
                    .retrieve()
                    .body(Map.class);
            String method = res != null && res.get("method") != null ? res.get("method").toString() : "UNKNOWN";
            String status = res != null && res.get("status") != null ? res.get("status").toString() : "DONE";
            return new TossConfirmResult(paymentKey, method, status);
        } catch (RestClientResponseException e) {
            log.warn("[TOSS] confirm failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.PAYMENT_CONFIRM_FAILED, e);
        } catch (RestClientException e) {   // 연결/읽기 타임아웃 등 I/O 실패(ResourceAccessException 포함)
            log.warn("[TOSS] confirm I/O error: {}", e.getMessage());
            throw new CustomException(ErrorCode.PAYMENT_CONFIRM_FAILED, e);
        }
    }

    /** 결제 취소(환불). cancelAmount 미지정 시 전액 취소. */
    public void cancel(String paymentKey, String cancelReason) {
        if (!enabled) {
            log.info("[TOSS-MOCK] cancel skipped (toss.enabled=false) paymentKey={}", paymentKey);
            return;
        }
        try {
            restClient.post()
                    .uri(String.format(CANCEL_PATH, paymentKey))
                    .header(HttpHeaders.AUTHORIZATION, authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "cancel-" + paymentKey)
                    .body(Map.of("cancelReason", cancelReason == null ? "관리자 취소" : cancelReason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("[TOSS] cancel failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_FAILED, e);
        } catch (RestClientException e) {
            log.warn("[TOSS] cancel I/O error: {}", e.getMessage());
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_FAILED, e);
        }
    }

    /** BigDecimal 금액 → KRW 정수(원). 소수부가 있으면 잘못된 금액으로 보고 400. */
    private long toWon(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, e);
        }
    }

    /** 토스 승인 응답에서 우리가 쓰는 값만 추린 결과. */
    public record TossConfirmResult(String paymentKey, String method, String status) {}
}
