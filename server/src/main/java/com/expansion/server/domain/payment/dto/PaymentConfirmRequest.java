package com.expansion.server.domain.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 결제 승인 요청 — 토스 인증 후 successUrl로 돌아온 값을 프론트가 그대로 전달.
 * amount는 위변조 검증용(서버 사전 저장값과 대조). 신뢰 기준은 서버 저장값.
 */
public record PaymentConfirmRequest(
        @NotNull String paymentKey,
        @NotNull String orderId,
        @NotNull BigDecimal amount
) {}
