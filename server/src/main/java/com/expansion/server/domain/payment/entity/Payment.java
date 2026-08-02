package com.expansion.server.domain.payment.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 1건. 토스페이먼츠 승인(confirm) 결과와 우리 에스크로 상태를 함께 보관한다.
 *
 * status 흐름(커미션 에스크로):
 *   PENDING(결제 요청 전 사전 저장) → HELD(승인·플랫폼 보관) → RELEASED(완료·작가 지급 예정)
 *                                                          ↘ REFUNDED(취소·환불)
 *   에셋 즉시판매는 PENDING → SUCCESS(즉시 판매, 에스크로 없음).
 *   승인 실패는 FAILED.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    /** 결제한 사용자(의뢰자/구매자). FK는 DB가 강제하므로 여기선 raw 컬럼으로만 매핑. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 우리가 발급해 토스에 넘기는 주문번호. 승인 시 위변조 대조 기준. */
    @Column(name = "order_id", length = 64)
    private String orderId;

    /** 토스 결제 식별자. 승인 성공 후 채워짐(취소 시 이 값으로 호출). */
    @Column(name = "payment_key", length = 255)
    private String paymentKey;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    /** 플랫폼 수수료(현재 MVP는 0). 작가 지급액 = amount - total_commission. */
    @Column(name = "total_commission", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCommission;

    /** 결제 수단(카드/간편결제 등). 승인 전엔 임시값, 승인 후 토스 응답으로 갱신. */
    @Column(name = "method", nullable = false, length = 50)
    private String method;

    /** PENDING / SUCCESS / FAILED / HELD / RELEASED / REFUNDED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Payment(Long userId, String orderId, BigDecimal amount, BigDecimal totalCommission,
                   String method, String status) {
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.totalCommission = totalCommission != null ? totalCommission : BigDecimal.ZERO;
        this.method = method != null ? method : "UNKNOWN";
        this.status = status != null ? status : "PENDING";
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    // ── 상태 전이 ─────────────────────────────────────────────
    /** 승인 성공 → 플랫폼 보관(커미션 에스크로). */
    public void markHeld(String paymentKey, String method) {
        this.paymentKey = paymentKey;
        if (method != null) this.method = method;
        this.status = "HELD";
    }

    /** 승인 성공 → 즉시 판매 확정(에셋 등 에스크로 불필요). */
    public void markSuccess(String paymentKey, String method) {
        this.paymentKey = paymentKey;
        if (method != null) this.method = method;
        this.status = "SUCCESS";
    }

    /** 거래 완료 → 작가 지급 예정. */
    public void markReleased() {
        this.status = "RELEASED";
    }

    /** 취소 → 환불 완료. */
    public void markRefunded() {
        this.status = "REFUNDED";
    }

    public void markFailed() {
        this.status = "FAILED";
    }
}
