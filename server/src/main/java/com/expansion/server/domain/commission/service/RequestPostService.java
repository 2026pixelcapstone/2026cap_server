package com.expansion.server.domain.commission.service;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.entity.CommissionApplication;
import com.expansion.server.domain.commission.entity.RequestPost;
import com.expansion.server.domain.commission.repository.CommissionApplicationRepository;
import com.expansion.server.domain.commission.repository.RequestPostRepository;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestPostService {

    private final RequestPostRepository requestPostRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final CommissionApplicationRepository applicationRepository;

    // 공개 목록 (OPEN 상태) — keyword 선택 검색
    public Page<RequestPostSummary> getOpenList(String keyword, Pageable pageable) {
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return requestPostRepository.search("OPEN", normalizedKeyword, pageable)
                .map(post -> {
                    Profile profile = profileRepository.findByUser_UserId(post.getClient().getUserId()).orElse(null);
                    return RequestPostSummary.of(post, profile);
                });
    }

    // 내가 등록한 의뢰 목록
    public Page<RequestPostSummary> getMyList(Long userId, Pageable pageable) {
        return requestPostRepository.findByClient_UserId(userId, pageable)
                .map(post -> {
                    Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
                    return RequestPostSummary.of(post, profile);
                });
    }

    // 상세
    public RequestPostResponse getPost(Long requestPostId) {
        RequestPost post = findById(requestPostId);
        Profile profile = profileRepository.findByUser_UserId(post.getClient().getUserId()).orElse(null);
        return RequestPostResponse.of(post, profile);
    }

    // 등록
    @Transactional
    public RequestPostResponse create(Long clientId, RequestPostCreateRequest request) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        RequestPost post = RequestPost.builder()
                .client(client)
                .title(request.getTitle())
                .description(request.getDescription())
                .budgetMin(request.getBudgetMin())
                .budgetMax(request.getBudgetMax())
                .deadline(request.getDeadline())
                .build();

        requestPostRepository.save(post);

        Profile profile = profileRepository.findByUser_UserId(clientId).orElse(null);
        return RequestPostResponse.of(post, profile);
    }

    // 수정
    @Transactional
    public RequestPostResponse update(Long clientId, Long requestPostId, RequestPostUpdateRequest request) {
        RequestPost post = findById(requestPostId);

        if (!post.getClient().getUserId().equals(clientId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if ("CLOSED".equals(post.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        post.update(request.getTitle(), request.getDescription(),
                request.getBudgetMin(), request.getBudgetMax(), request.getDeadline());

        Profile profile = profileRepository.findByUser_UserId(clientId).orElse(null);
        return RequestPostResponse.of(post, profile);
    }

    // 마감 처리
    @Transactional
    public void close(Long clientId, Long requestPostId) {
        RequestPost post = findById(requestPostId);

        if (!post.getClient().getUserId().equals(clientId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        post.close();

        // 남은 PENDING 지원은 일괄 거절 (이미 수락된 작가들의 진행 중 커미션은 그대로 유지)
        applicationRepository.findByRequestPost_RequestPostIdAndStatus(requestPostId, "PENDING")
                .forEach(CommissionApplication::reject);
    }

    // 삭제
    @Transactional
    public void delete(Long clientId, Long requestPostId) {
        RequestPost post = findById(requestPostId);

        if (!post.getClient().getUserId().equals(clientId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        requestPostRepository.delete(post);
    }

    private RequestPost findById(Long requestPostId) {
        return requestPostRepository.findById(requestPostId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }
}
