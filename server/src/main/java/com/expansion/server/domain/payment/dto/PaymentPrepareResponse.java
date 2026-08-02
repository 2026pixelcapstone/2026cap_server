package com.expansion.server.domain.payment.dto;

import java.math.BigDecimal;

/**
 * 결제 준비 응답 — 프론트 결제위젯이 requestPayment에 넘길 값.
 * clientKey는 프론트 공개용 키(빌드 env로도 주입하지만, 서버가 정본을 내려줘 불일치 방지).
 */
public record PaymentPrepareResponse(
        String orderId,
        BigDecimal amount,
        String orderName,
        String clientKey
) {}
