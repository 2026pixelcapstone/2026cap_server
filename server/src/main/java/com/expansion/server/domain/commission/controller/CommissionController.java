package com.expansion.server.domain.commission.controller;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.service.CommissionService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    @PostMapping("/{commissionId}/files")
    public ApiResponse<CommissionResponse> uploadFile(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @RequestBody FileUploadRequest request) {
        return ApiResponse.ok(commissionService.uploadFile(
                userId, commissionId,
                request.getFileType(), request.getFileUrl(),
                request.getFileName(), request.getFileSize()));
    }

    // 작가 납품 파일 1개 삭제.
    @DeleteMapping("/{commissionId}/files/{fileId}")
    public ApiResponse<CommissionResponse> deleteDeliveryFile(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @PathVariable Long fileId) {
        return ApiResponse.ok(commissionService.deleteDeliveryFile(userId, commissionId, fileId));
    }

    // 작가 검토용 미리보기 이미지 업로드 (여러 장, 서버가 각각 워터마크+축소). 멀티파트.
    @PostMapping("/{commissionId}/previews")
    public ApiResponse<CommissionResponse> uploadPreviews(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @RequestParam("images") List<MultipartFile> images) {
        return ApiResponse.ok(commissionService.uploadPreviews(userId, commissionId, images));
    }

    // 작가 검토용 미리보기 이미지 1장 삭제.
    @DeleteMapping("/{commissionId}/previews/{previewImageId}")
    public ApiResponse<CommissionResponse> deletePreview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @PathVariable Long previewImageId) {
        return ApiResponse.ok(commissionService.deletePreview(userId, commissionId, previewImageId));
    }

    // ─── Inner request classes ────────────────────────────────────────────────

    @Getter
    @NoArgsConstructor
    static class FileUploadRequest {
        private String fileType;  // DRAFT / FINAL
        private String fileUrl;
        private String fileName;
        private Long fileSize;
    }
}
