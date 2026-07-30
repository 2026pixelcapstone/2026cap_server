package com.expansion.server.domain.commission.controller;

import com.expansion.server.domain.commission.dto.ArtistRatingSummary;
import com.expansion.server.domain.commission.dto.CommissionReviewRequest;
import com.expansion.server.domain.commission.dto.CommissionReviewResponse;
import com.expansion.server.domain.commission.service.CommissionReviewService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 커미션 평점/리뷰 — 완료 거래에 대해 의뢰자가 작가를 평가. 작가별 집계는 카드/상세 신뢰 신호.
 */
@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
public class CommissionReviewController {

    private final CommissionReviewService reviewService;

    // 리뷰 작성/수정 (의뢰자·COMPLETED·거래당 1건)
    @PostMapping("/{commissionId}/review")
    public ApiResponse<CommissionReviewResponse> writeReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @Valid @RequestBody CommissionReviewRequest request) {
        return ApiResponse.ok(reviewService.writeReview(userId, commissionId, request));
    }

    // 내 리뷰 조회 (작성 폼 프리필) — 없으면 data=null
    @GetMapping("/{commissionId}/review")
    public ApiResponse<CommissionReviewResponse> getMyReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId) {
        return ApiResponse.ok(reviewService.getMyReview(userId, commissionId));
    }

    // 작가 리뷰 목록 (서비스 상세, 공개)
    @GetMapping("/artists/{artistId}/reviews")
    public ApiResponse<Page<CommissionReviewResponse>> getArtistReviews(
            @PathVariable Long artistId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(reviewService.getArtistReviews(artistId, pageable));
    }

    // 작가별 신뢰 신호 배치 (평점·완료건수, 카드용, 공개)
    @GetMapping("/artists/rating-summary")
    public ApiResponse<Map<Long, ArtistRatingSummary>> getRatingSummaries(
            @RequestParam List<Long> artistIds) {
        return ApiResponse.ok(reviewService.getRatingSummaries(artistIds));
    }
}
