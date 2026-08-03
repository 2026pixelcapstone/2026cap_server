package com.expansion.server.domain.payment.entity;

/**
 * 결제 상태. VARCHAR 컬럼에 이름 그대로 저장(@Enumerated(STRING)).
 *
 * <p>커미션 에스크로: PENDING → HELD → RELEASED (또는 REFUNDED).
 * 에셋 즉시판매: PENDING → SUCCESS. 승인 실패: FAILED.
 */
public enum PaymentStatus {
    /** 결제 요청 전 사전 저장(orderId 발급). */
    PENDING,
    /** 즉시 판매 확정(에셋 등, 에스크로 없음). */
    SUCCESS,
    /** 승인 실패. */
    FAILED,
    /** 승인·플랫폼 보관(커미션 에스크로). */
    HELD,
    /** 거래 완료·작가 지급 예정. */
    RELEASED,
    /** 취소·환불 완료. */
    REFUNDED
}
