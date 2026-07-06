package com.expansion.server.domain.commission.controller;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.service.CommissionService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService commissionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommissionResponse> createCommission(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CommissionCreateRequest request) {
        return ApiResponse.ok(commissionService.createCommission(userId, request));
    }

    @GetMapping("/{commissionId}")
    public ApiResponse<CommissionResponse> getCommission(
            @PathVariable Long commissionId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(commissionService.getCommission(commissionId, userId));
    }

    @GetMapping("/my/client")
    public ApiResponse<Page<CommissionSummary>> getMyCommissionsAsClient(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(commissionService.getMyCommissionsAsClient(userId, pageable));
    }

    @GetMapping("/my/artist")
    public ApiResponse<Page<CommissionSummary>> getMyCommissionsAsArtist(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(commissionService.getMyCommissionsAsArtist(userId, pageable));
    }

    @PatchMapping("/{commissionId}/status")
    public ApiResponse<CommissionResponse> updateStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @RequestBody CommissionUpdateRequest request) {
        return ApiResponse.ok(commissionService.updateStatus(userId, commissionId, request));
    }

    @PostMapping("/{commissionId}/cancel")
    public ApiResponse<Void> cancelCommission(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId) {
        commissionService.cancelCommission(userId, commissionId);
        return ApiResponse.ok("커미션이 취소되었습니다.");
    }

    /**
     * 납품/참고 파일 업로드 (multipart, 다중) — "원본 = 미리보기" 재설계.
     * 서버가 원본을 R2에 저장하고, 작가 납품 이미지면 워터마크 미리보기를 자동 생성한다.
     * (기존: 프론트가 R2 업로드 후 URL JSON 전달 + 미리보기 별도 업로드 → 폐지)
     */
    @PostMapping("/{commissionId}/files")
    public ApiResponse<CommissionResponse> uploadDeliveryFiles(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "fileType", defaultValue = "FINAL") String fileType) {
        return ApiResponse.ok(commissionService.uploadDeliveryFiles(userId, commissionId, files, fileType));
    }

    // 작가 납품 파일 1개 삭제 (연동 자동 생성 미리보기도 함께 삭제).
    @DeleteMapping("/{commissionId}/files/{fileId}")
    public ApiResponse<CommissionResponse> deleteDeliveryFile(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @PathVariable Long fileId) {
        return ApiResponse.ok(commissionService.deleteDeliveryFile(userId, commissionId, fileId));
    }
}
