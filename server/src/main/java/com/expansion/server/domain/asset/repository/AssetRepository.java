package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.Asset;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    /**
     * 버전 등록 시 에셋 행을 비관적 락으로 잡아 동시 요청을 직렬화한다.
     * (같은 에셋에 버전이 동시에 추가되면 동일 nextNumber를 읽어
     *  (asset_id, version_number) 유니크 제약을 위반하는 것 방지.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Asset a WHERE a.assetId = :assetId")
    Optional<Asset> findByIdForUpdate(@Param("assetId") Long assetId);

    // 카운터는 원자적 UPDATE로 증가 (엔티티 ++는 동시 요청 시 lost update 발생)
    @Modifying
    @Query("UPDATE Asset a SET a.viewCount = a.viewCount + 1 WHERE a.assetId = :assetId")
    void incrementViewCount(@Param("assetId") Long assetId);

    @Modifying
    @Query("UPDATE Asset a SET a.downloadCount = a.downloadCount + 1 WHERE a.assetId = :assetId")
    void incrementDownloadCount(@Param("assetId") Long assetId);

    Page<Asset> findByStatus(String status, Pageable pageable);

    Page<Asset> findByStatusAndIsFree(String status, boolean isFree, Pageable pageable);

    Page<Asset> findByUser_UserId(Long userId, Pageable pageable);

    // 목록 조회 — categoryId/isFree 선택 필터(둘 다 null이면 전체 ACTIVE)
    @Query("""
            SELECT a FROM Asset a
            WHERE a.status = 'ACTIVE'
            AND (:categoryId IS NULL OR a.category.categoryId = :categoryId)
            AND (:isFree IS NULL OR a.isFree = :isFree)
            """)
    Page<Asset> findActiveAssets(@Param("categoryId") Long categoryId,
                                 @Param("isFree") Boolean isFree,
                                 Pageable pageable);

    @Query("SELECT a FROM Asset a JOIN a.assetTags at WHERE at.tag.tagName = :tagName AND a.status = 'ACTIVE' AND (:isFree IS NULL OR a.isFree = :isFree)")
    Page<Asset> findByTagName(@Param("tagName") String tagName, @Param("isFree") Boolean isFree, Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.status = 'ACTIVE' AND (a.title LIKE %:keyword% OR a.description LIKE %:keyword%)")
    Page<Asset> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
