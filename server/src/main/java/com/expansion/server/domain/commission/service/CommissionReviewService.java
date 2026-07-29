package com.expansion.server.domain.commission.service;

import com.expansion.server.domain.commission.dto.ArtistRatingSummary;
import com.expansion.server.domain.commission.dto.CommissionReviewRequest;
import com.expansion.server.domain.commission.dto.CommissionReviewResponse;
import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.commission.entity.CommissionReview;
import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.domain.commission.repository.CommissionReviewRepository;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 커미션 평점/리뷰 — 완료 거래에 대해 의뢰자가 작가를 평가(거래당 1리뷰).
 * 작가별 집계(평점·완료건수)는 배치 조회로 카드 N+1 방지.
 */
@Service
@RequiredArgsConstructor
public class CommissionReviewService {

    private final CommissionRepository commissionRepository;
    private final CommissionReviewRepository reviewRepository;
    private final ProfileRepository profileRepository;

    /** 리뷰 작성/수정 — 해당 거래의 의뢰자만, COMPLETED 거래만, 거래당 1건(있으면 갱신). */
    @Transactional
    public CommissionReviewResponse writeReview(Long userId, Long commissionId, CommissionReviewRequest request) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        if (!commission.getClient().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);   // 의뢰자만 작성
        }
        if (!"COMPLETED".equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.COMMISSION_NOT_COMPLETED);
        }

        CommissionReview review = reviewRepository.findByCommission_CommissionId(commissionId)
                .map(existing -> {
                    existing.update(request.getRating(), request.getContent());
                    return existing;
                })
                .orElseGet(() -> {
                    try {
                        return reviewRepository.saveAndFlush(CommissionReview.builder()
                                .commission(commission)
                                .reviewer(commission.getClient())
                                .artist(commission.getArtist())
                                .rating(request.getRating())
                                .content(request.getContent())
                                .build());
                    } catch (DataIntegrityViolationException e) {
                        // commission_id 유니크 위반(동시 중복 제출)만 409로 변환. 그 외 무결성 오류는
                        // 감추지 않고 그대로 전파(잘못된 409 오응답 방지).
                        if (isCommissionUniqueViolation(e)) {
                            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);
                        }
                        throw e;
                    }
                });

        Profile reviewerProfile = profileRepository.findByUser_UserId(userId).orElse(null);
        return CommissionReviewResponse.of(review, reviewerProfile);
    }

    // commission_id 유니크 제약 위반인지 판별 (제약 이름으로 정밀 판별 — 다른 무결성 오류를 409로 감추지 않기 위함)
    private boolean isCommissionUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        return cause instanceof ConstraintViolationException cve
                && "uk_commission_reviews_commission".equalsIgnoreCase(cve.getConstraintName());
    }

    /** 내 리뷰 조회 — 작성 폼 프리필용. 없으면 null. */
    @Transactional(readOnly = true)
    public CommissionReviewResponse getMyReview(Long userId, Long commissionId) {
        CommissionReview review = reviewRepository.findByCommission_CommissionId(commissionId).orElse(null);
        if (review == null || !review.getReviewer().getUserId().equals(userId)) {
            return null;
        }
        Profile reviewerProfile = profileRepository.findByUser_UserId(userId).orElse(null);
        return CommissionReviewResponse.of(review, reviewerProfile);
    }

    /** 작가 리뷰 목록 (서비스 상세). */
    @Transactional(readOnly = true)
    public Page<CommissionReviewResponse> getArtistReviews(Long artistId, Pageable pageable) {
        Page<CommissionReview> page = reviewRepository.findByArtist_UserIdOrderByCreatedAtDesc(artistId, pageable);
        List<Long> reviewerIds = page.getContent().stream()
                .map(r -> r.getReviewer().getUserId()).distinct().toList();
        Map<Long, Profile> profileMap = new HashMap<>();
        for (Profile p : profileRepository.findAllByUser_UserIdIn(reviewerIds)) {
            profileMap.put(p.getUser().getUserId(), p);
        }
        return page.map(r -> CommissionReviewResponse.of(r, profileMap.get(r.getReviewer().getUserId())));
    }

    // 공개 배치 조회 — 과도한 IN 절 방지용 요청 상한(카드는 페이지당 수 개라 넉넉)
    private static final int MAX_RATING_SUMMARY_IDS = 100;

    /** 작가별 신뢰 신호 배치 — {artistId: {평균, 리뷰수, 완료건수}}. 요청한 모든 id 포함(없으면 empty). */
    @Transactional(readOnly = true)
    public Map<Long, ArtistRatingSummary> getRatingSummaries(List<Long> artistIds) {
        if (artistIds == null || artistIds.isEmpty()) return Map.of();
        if (artistIds.size() > MAX_RATING_SUMMARY_IDS) {
            throw new CustomException(ErrorCode.INVALID_INPUT);   // 공개 엔드포인트 남용 방지
        }

        Map<Long, double[]> rating = new HashMap<>();   // artistId → [avg, count]
        for (var row : reviewRepository.findRatingSummaryByArtistIds(artistIds)) {
            rating.put(row.getArtistId(), new double[]{
                    row.getAverage() != null ? row.getAverage() : 0.0,
                    row.getCount() != null ? row.getCount() : 0L
            });
        }
        Map<Long, Long> completed = new HashMap<>();
        for (var row : commissionRepository.findCompletedCountByArtistIds(artistIds)) {
            completed.put(row.getArtistId(), row.getCount() != null ? row.getCount() : 0L);
        }

        Map<Long, ArtistRatingSummary> result = new HashMap<>();
        for (Long id : artistIds) {
            double[] r = rating.getOrDefault(id, new double[]{0.0, 0L});
            long completedCount = completed.getOrDefault(id, 0L);
            result.put(id, new ArtistRatingSummary(r[0], (long) r[1], completedCount));
        }
        return result;
    }
}
