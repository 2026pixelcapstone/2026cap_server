package com.expansion.server.domain.commission.dto;

/**
 * 작가별 신뢰 신호 요약 — 서비스 카드/상세용 배치 응답.
 * average: 평균 평점(리뷰 없으면 0), reviewCount: 리뷰 수, completedCount: 완료 거래 수.
 * 프론트는 reviewCount가 임계값(예: 4) 미만이면 "평가 부족"으로 표시.
 */
public record ArtistRatingSummary(
        double average,
        long reviewCount,
        long completedCount
) {
    public static ArtistRatingSummary empty() {
        return new ArtistRatingSummary(0.0, 0L, 0L);
    }
}
