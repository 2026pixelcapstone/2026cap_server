package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.AssetComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetCommentRepository extends JpaRepository<AssetComment, Long> {

    Page<AssetComment> findByAsset_AssetIdAndParentIsNull(Long assetId, Pageable pageable);

    // 한 유저가 이 에셋에 남긴 리뷰(별점 있는 미삭제 최상위 댓글) — 유저당 1리뷰 보장/갱신용
    Optional<AssetComment> findFirstByAsset_AssetIdAndUser_UserIdAndRatingIsNotNullAndIsDeletedFalse(
            Long assetId, Long userId);

    // 평균/개수 집계 — 단일 행 [avg(double|null), count(long)]. 미삭제·별점 있는 것만
    @Query("""
            SELECT AVG(c.rating), COUNT(c) FROM AssetComment c
            WHERE c.asset.assetId = :assetId AND c.rating IS NOT NULL AND c.isDeleted = false
            """)
    List<Object[]> aggregateRating(@Param("assetId") Long assetId);

    // 별점 분포 — [rating, count] 행
    @Query("""
            SELECT c.rating, COUNT(c) FROM AssetComment c
            WHERE c.asset.assetId = :assetId AND c.rating IS NOT NULL AND c.isDeleted = false
            GROUP BY c.rating
            """)
    List<Object[]> ratingDistribution(@Param("assetId") Long assetId);
}
