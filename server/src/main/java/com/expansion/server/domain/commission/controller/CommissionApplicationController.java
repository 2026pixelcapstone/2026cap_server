package com.expansion.server.domain.commission.controller;

import com.expansion.server.domain.commission.dto.CommissionApplicationCreateRequest;
import com.expansion.server.domain.commission.dto.CommissionApplicationResponse;
import com.expansion.server.domain.commission.service.CommissionApplicationService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class CommissionApplicationController {

    private final CommissionApplicationService applicationService;

    // 지원하기
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommissionApplicationResponse> apply(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CommissionApplicationCreateRequest request) {
        return ApiResponse.ok(applicationService.apply(userId, request));
    }

    // 작가의 내 지원 목록
    @GetMapping("/my")
    public ApiResponse<Page<CommissionApplicationResponse>> getMyApplications(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(applicationService.getMyApplications(userId, pageable));
    }

    // 의뢰자가 특정 의뢰의 지원 목록 조회
    @GetMapping("/by-post/{requestPostId}")
    public ApiResponse<Page<CommissionApplicationResponse>> getApplicationsByPost(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestPostId,
            @PageableDefault(size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(applicationService.getApplicationsByPost(userId, requestPostId, pageable));
    }

    // 지원 수락 (의뢰자) → 생성된 커미션 ID 반환
    @PostMapping("/{applicationId}/accept")
    public ApiResponse<Long> accept(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long applicationId) {
        Long commissionId = applicationService.accept(userId, applicationId);
        return ApiResponse.ok(commissionId);
    }

    // 지원 취소 (작가, PENDING 상태만)
    @DeleteMapping("/{applicationId}")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long applicationId) {
        applicationService.cancel(userId, applicationId);
        return ApiResponse.ok("지원이 취소되었습니다.");
    }
}
