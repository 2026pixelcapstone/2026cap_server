package com.expansion.server.domain.commission.repository;

import com.expansion.server.domain.commission.entity.CommissionReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommissionReviewRepository extends JpaRepository<CommissionReview, Long> {

    // 거래당 1리뷰 — 조회(작성 폼 프리필) 및 중복 판별(있으면 UPDATE)
    Optional<CommissionReview> findByCommission_CommissionId(Long commissionId);

    // 작가 리뷰 목록 (프로필/서비스 상세)
    Page<CommissionReview> findByArtist_UserIdOrderByCreatedAtDesc(Long artistId, Pageable pageable);

    // 작가별 평점 집계 배치 (카드 N+1 방지) — 평균·개수
    @Query("""
            SELECT r.artist.userId AS artistId, AVG(r.rating) AS average, COUNT(r) AS count
            FROM CommissionReview r
            WHERE r.artist.userId IN :artistIds
            GROUP BY r.artist.userId
            """)
    List<ArtistRatingRow> findRatingSummaryByArtistIds(@Param("artistIds") List<Long> artistIds);

    // 배치 집계 projection
    interface ArtistRatingRow {
        Long getArtistId();
        Double getAverage();
        Long getCount();
    }
}
