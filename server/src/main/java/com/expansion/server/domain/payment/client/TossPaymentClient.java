package com.expansion.server.domain.payment.client;

import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    private final boolean enabled;
    private final String secretKey;
    private final RestClient restClient;

    public TossPaymentClient(
            @Value("${toss.enabled:false}") boolean enabled,
            @Value("${toss.secret-key:}") String secretKey,
            @Value("${toss.base-url:https://api.tosspayments.com}") String baseUrl) {
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    private String authHeader() {
        String raw = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 결제 승인. 성공 시 실제 카드가 청구된다. paymentKey/method를 담은 결과 반환. */
    public TossConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount) {
        if (!enabled) {
            // 로컬 목킹: 실제 청구 없이 성공 처리(상태 전이 검증용).
            log.info("[TOSS-MOCK] confirm skipped (toss.enabled=false) orderId={} amount={}", orderId, amount);
            return new TossConfirmResult(paymentKey, "간편결제", "DONE");
        }
        try {
            Map<?, ?> res = restClient.post()
                    .uri(CONFIRM_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    // Idempotency-Key: 같은 orderId 재승인 시 중복 청구 방지
                    .header("Idempotency-Key", orderId)
                    .body(Map.of(
                            "paymentKey", paymentKey,
                            "orderId", orderId,
                            "amount", amount.longValueExact()))  // KRW 정수
                    .retrieve()
                    .body(Map.class);
            String method = res != null && res.get("method") != null ? res.get("method").toString() : "UNKNOWN";
            String status = res != null && res.get("status") != null ? res.get("status").toString() : "DONE";
            return new TossConfirmResult(paymentKey, method, status);
        } catch (RestClientResponseException e) {
            log.warn("[TOSS] confirm failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.PAYMENT_CONFIRM_FAILED);
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
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_FAILED);
        }
    }

    /** 토스 승인 응답에서 우리가 쓰는 값만 추린 결과. */
    public record TossConfirmResult(String paymentKey, String method, String status) {}
}
