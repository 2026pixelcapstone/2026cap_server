package com.expansion.server.domain.payment.dto;

/**
 * 결제 승인 결과. type으로 커미션/에셋 구분 → 프론트가 알맞은 상세로 이동.
 * COMMISSION이면 commissionId, ASSET이면 assetId가 채워진다.
 */
public record PaymentConfirmResult(
        String type,          // "COMMISSION" | "ASSET"
        Long commissionId,
        Long assetId,
        Long paymentId
) {}
