package com.expansion.server.domain.commission.controller;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.service.RequestPostService;
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
@RequestMapping("/api/request-posts")
@RequiredArgsConstructor
public class RequestPostController {

    private final RequestPostService requestPostService;

    // 공개 목록 (비로그인 허용) — keyword 선택 검색
    @GetMapping
    public ApiResponse<Page<RequestPostSummary>> getOpenList(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(requestPostService.getOpenList(keyword, pageable));
    }

    // 내가 등록한 의뢰 목록
    @GetMapping("/my")
    public ApiResponse<Page<RequestPostSummary>> getMyList(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(requestPostService.getMyList(userId, pageable));
    }

    // 상세
    @GetMapping("/{requestPostId}")
    public ApiResponse<RequestPostResponse> getPost(@PathVariable Long requestPostId) {
        return ApiResponse.ok(requestPostService.getPost(requestPostId));
    }

    // 등록
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RequestPostResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RequestPostCreateRequest request) {
        return ApiResponse.ok(requestPostService.create(userId, request));
    }

    // 수정
    @PatchMapping("/{requestPostId}")
    public ApiResponse<RequestPostResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestPostId,
            @RequestBody RequestPostUpdateRequest request) {
        return ApiResponse.ok(requestPostService.update(userId, requestPostId, request));
    }

    // 마감 처리
    @PostMapping("/{requestPostId}/close")
    public ApiResponse<Void> close(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestPostId) {
        requestPostService.close(userId, requestPostId);
        return ApiResponse.ok("의뢰가 마감되었습니다.");
    }

    // 삭제
    @DeleteMapping("/{requestPostId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long requestPostId) {
        requestPostService.delete(userId, requestPostId);
        return ApiResponse.ok("의뢰가 삭제되었습니다.");
    }
}
