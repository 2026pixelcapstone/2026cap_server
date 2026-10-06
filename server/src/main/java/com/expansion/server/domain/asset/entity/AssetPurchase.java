package com.expansion.server.domain.asset.entity;

import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 에셋 소유권 기록 — 유료 구매(payment_id 있음)와 무료 취득(payment_id NULL, price_paid 0)을 함께 담는다.
 * - 구매마다 행을 새로 만들고 지우거나 덮어쓰지 않음(환불은 REFUNDED로 표시만 → 이력 보존).
 * - 활성(ACTIVE) 행은 사람·에셋당 1개만: 부분 유니크 인덱스 uq_asset_purchases_active(V34).
 * - ⚠️ 매출이 아님: 판매 집계는 반드시 payment_id IS NOT NULL로 거를 것.
 */
@Entity
@Table(name = "asset_purchases")
@Getter
@NoArgsConstructor
public class AssetPurchase {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REFUNDED = "REFUNDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Long purchaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "price_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePaid;

    @Column(nullable = false, length = 20)
    private String status;
    // ACTIVE / REFUNDED

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AssetPurchase(User user, Asset asset, Long paymentId, BigDecimal pricePaid) {
        this.user = user;
        this.asset = asset;
        this.paymentId = paymentId;
        this.pricePaid = pricePaid != null ? pricePaid : BigDecimal.ZERO;
        this.status = STATUS_ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = STATUS_ACTIVE;
        if (this.pricePaid == null) this.pricePaid = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 환불 — 행은 남기고 상태만 바꿈(재구매는 새 행). 소유권은 ACTIVE 행으로만 판단하므로 즉시 권리 소멸. */
    public void refund() {
        this.status = STATUS_REFUNDED;
    }

    /** 결제 없이 얻은 무료 취득 행인지 */
    public boolean isFreeAcquisition() {
        return this.paymentId == null;
    }
}
