package com.expansion.server.domain.payment.dto;

/**
 * 결제 승인 결과 — 프론트가 거래룸으로 이동/갱신할 수 있도록 커미션 식별자와 전이된 상태를 반환.
 */
public record PaymentConfirmResult(
        Long commissionId,
        String commissionStatus,
        Long paymentId
) {}
