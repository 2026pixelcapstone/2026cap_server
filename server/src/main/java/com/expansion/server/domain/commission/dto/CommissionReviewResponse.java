package com.expansion.server.domain.commission.dto;

import com.expansion.server.domain.commission.entity.CommissionReview;
import com.expansion.server.domain.user.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 커미션 리뷰 응답 — 작성 폼 프리필 + 작가 리뷰 목록에서 사용.
 * 작성자(의뢰자) 프로필은 목록 표시용.
 */
@Getter
@Builder
public class CommissionReviewResponse {

    private Long reviewId;
    private Long commissionId;
    private Long reviewerId;
    private String reviewerNickname;
    private String reviewerProfileImageUrl;
    private Long artistId;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommissionReviewResponse of(CommissionReview r, Profile reviewerProfile) {
        return CommissionReviewResponse.builder()
                .reviewId(r.getReviewId())
                .commissionId(r.getCommission().getCommissionId())
                .reviewerId(r.getReviewer().getUserId())
                .reviewerNickname(reviewerProfile != null ? reviewerProfile.getNickname() : null)
                .reviewerProfileImageUrl(reviewerProfile != null ? reviewerProfile.getProfileImageUrl() : null)
                .artistId(r.getArtist().getUserId())
                .rating(r.getRating())
                .content(r.getContent())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
