package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.AssetPurchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 에셋 소유권(유료 구매 + 무료 취득). 소유 여부는 반드시 ACTIVE 행으로만 판단한다(REFUNDED는 권리 없음).
 */
public interface AssetPurchaseRepository extends JpaRepository<AssetPurchase, Long> {

    /** 소유 여부 — ACTIVE 행 존재. 다운로드·평점·구매 중복 판정의 유일한 기준. */
    @Query("""
            SELECT COUNT(p) > 0 FROM AssetPurchase p
            WHERE p.user.userId = :userId AND p.asset.assetId = :assetId AND p.status = 'ACTIVE'
            """)
    boolean existsActive(@Param("userId") Long userId, @Param("assetId") Long assetId);

    /** 실제 결제 이력(ACTIVE·REFUNDED 무관)이 있는지 — 있으면 에셋을 지우지 않고 판매 중지로 남긴다. */
    boolean existsByAsset_AssetIdAndPaymentIdIsNotNull(Long assetId);

    /** 무료 취득 행 전체 삭제 — 결제 이력 없는 에셋을 완전 삭제하기 전 FK 정리용. */
    @Modifying
    @Query("DELETE FROM AssetPurchase p WHERE p.asset.assetId = :assetId AND p.paymentId IS NULL")
    int deleteFreeAcquisitions(@Param("assetId") Long assetId);

    /**
     * 무료 취득 기록 — 이미 ACTIVE 소유가 있으면 무시(부분 유니크 uq_asset_purchases_active + ON CONFLICT).
     * 동시 다운로드도 DB가 직렬화하므로 예외 없이 1행만 남는다. 반환 = 삽입 행 수.
     */
    @Modifying
    @Query(value = """
            INSERT INTO asset_purchases (user_id, asset_id, payment_id, price_paid, status, created_at, updated_at)
            VALUES (:userId, :assetId, NULL, 0, 'ACTIVE', NOW(), NOW())
            ON CONFLICT (user_id, asset_id) WHERE status = 'ACTIVE' DO NOTHING
            """, nativeQuery = true)
    int insertFreeAcquisitionIfAbsent(@Param("userId") Long userId, @Param("assetId") Long assetId);

    /** 구매/받은 에셋 — 유료 구매(결제 있음) ACTIVE. 에셋·작성자는 함께 로드(N+1 방지). */
    @EntityGraph(attributePaths = {"asset", "asset.user"})
    Page<AssetPurchase> findByUser_UserIdAndStatusAndPaymentIdIsNotNull(Long userId, String status, Pageable pageable);

    /** 구매/받은 에셋 — 무료 취득(결제 없음) ACTIVE. */
    @EntityGraph(attributePaths = {"asset", "asset.user"})
    Page<AssetPurchase> findByUser_UserIdAndStatusAndPaymentIdIsNull(Long userId, String status, Pageable pageable);
}
