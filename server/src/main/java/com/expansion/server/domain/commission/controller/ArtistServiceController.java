package com.expansion.server.domain.commission.controller;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.service.ArtistServiceService;
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
@RequestMapping("/api/artist-services")
@RequiredArgsConstructor
public class ArtistServiceController {

    private final ArtistServiceService artistServiceService;

    // 공개 목록 (비로그인 허용) — category/keyword 선택 필터
    @GetMapping
    public ApiResponse<Page<ArtistServiceSummary>> getOpenList(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(artistServiceService.getOpenList(category, keyword, pageable));
    }

    // 내 서비스 목록
    @GetMapping("/my")
    public ApiResponse<Page<ArtistServiceSummary>> getMyList(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(artistServiceService.getMyList(userId, pageable));
    }

    // 상세
    @GetMapping("/{serviceId}")
    public ApiResponse<ArtistServiceResponse> getService(@PathVariable Long serviceId) {
        return ApiResponse.ok(artistServiceService.getService(serviceId));
    }

    // 등록
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistServiceResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ArtistServiceCreateRequest request) {
        return ApiResponse.ok(artistServiceService.create(userId, request));
    }

    // 수정
    @PatchMapping("/{serviceId}")
    public ApiResponse<ArtistServiceResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long serviceId,
            @RequestBody ArtistServiceUpdateRequest request) {
        return ApiResponse.ok(artistServiceService.update(userId, serviceId, request));
    }

    // 마감 처리
    @PostMapping("/{serviceId}/close")
    public ApiResponse<Void> close(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long serviceId) {
        artistServiceService.close(userId, serviceId);
        return ApiResponse.ok("서비스가 마감되었습니다.");
    }

    // 삭제
    @DeleteMapping("/{serviceId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long serviceId) {
        artistServiceService.delete(userId, serviceId);
        return ApiResponse.ok("서비스가 삭제되었습니다.");
    }
}
