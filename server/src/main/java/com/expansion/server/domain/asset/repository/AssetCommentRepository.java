package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.AssetComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 에셋의 댓글·리뷰 전체 삭제 — 에셋 완전 삭제 전 FK(asset_comments.asset_id, CASCADE 없음) 정리용.
     * 대댓글의 parent_id 자기참조도 한 문장 안에서 함께 지워지므로 위반 없음(NO ACTION은 문장 끝에 검사).
     */
    @Modifying
    @Query("DELETE FROM AssetComment c WHERE c.asset.assetId = :assetId")
    int deleteByAssetId(@Param("assetId") Long assetId);
}
