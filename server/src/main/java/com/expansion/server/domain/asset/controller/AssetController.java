package com.expansion.server.domain.asset.controller;

import com.expansion.server.domain.asset.dto.*;
import com.expansion.server.domain.asset.service.AssetService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    private Long resolveUserId(Long principal) {
        if (principal != null) return principal;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }

    // GET /api/assets?isFree=true&authorId=1
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AssetSummary>>> getAssetList(
            @RequestParam(required = false) Boolean isFree,
            @RequestParam(required = false) Long authorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        if (authorId != null && isFree != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("authorId와 isFree는 동시에 사용할 수 없습니다."));
        }
        if (authorId != null) {
            return ResponseEntity.ok(ApiResponse.success(assetService.getUserAssets(authorId, pageable)));
        }
        return ResponseEntity.ok(ApiResponse.success(assetService.getAssetList(isFree, pageable)));
    }

    // GET /api/assets/{assetId}
    @GetMapping("/{assetId}")
    public ResponseEntity<ApiResponse<AssetResponse>> getAsset(
            @PathVariable Long assetId,
            @AuthenticationPrincipal Long currentUserId) {

        return ResponseEntity.ok(ApiResponse.success(assetService.getAsset(assetId, resolveUserId(currentUserId))));
    }

    // POST /api/assets
    @PostMapping
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AssetCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(assetService.createAsset(userId, request)));
    }

    // PATCH /api/assets/{assetId}
    @PatchMapping("/{assetId}")
    public ResponseEntity<ApiResponse<AssetResponse>> updateAsset(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @Valid @RequestBody AssetUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.success(assetService.updateAsset(userId, assetId, request)));
    }

    // DELETE /api/assets/{assetId}
    @DeleteMapping("/{assetId}")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId) {

        assetService.deleteAsset(userId, assetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // POST /api/assets/{assetId}/like
    @PostMapping("/{assetId}/like")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId) {

        return ResponseEntity.ok(ApiResponse.success(assetService.toggleLike(userId, assetId)));
    }

    // POST /api/assets/{assetId}/purchase
    @PostMapping("/{assetId}/purchase")
    public ResponseEntity<ApiResponse<Void>> purchase(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId) {

        assetService.purchaseAsset(userId, assetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // GET /api/assets/{assetId}/rating-summary — 평점 평균/개수/분포(공개)
    @GetMapping("/{assetId}/rating-summary")
    public ResponseEntity<ApiResponse<AssetRatingSummaryResponse>> getRatingSummary(
            @PathVariable Long assetId) {
        return ResponseEntity.ok(ApiResponse.success(assetService.getRatingSummary(assetId)));
    }

    // GET /api/assets/{assetId}/comments
    @GetMapping("/{assetId}/comments")
    public ResponseEntity<ApiResponse<Page<AssetCommentResponse>>> getComments(
            @PathVariable Long assetId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(assetService.getComments(assetId, pageable)));
    }

    // POST /api/assets/{assetId}/comments
    @PostMapping("/{assetId}/comments")
    public ResponseEntity<ApiResponse<AssetCommentResponse>> createComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @Valid @RequestBody AssetCommentCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(assetService.createComment(userId, assetId, request)));
    }

    // DELETE /api/assets/{assetId}/comments/{commentId}
    @DeleteMapping("/{assetId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId,
            @PathVariable Long commentId) {

        assetService.deleteComment(userId, commentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // GET /api/assets/search?keyword=xxx
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<AssetSummary>>> search(
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(assetService.searchAssets(keyword, pageable)));
    }

    // GET /api/assets/tags/{tagName}
    @GetMapping("/tags/{tagName}")
    public ResponseEntity<ApiResponse<Page<AssetSummary>>> getByTag(
            @PathVariable String tagName,
            @RequestParam(required = false) Boolean isFree,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(assetService.getAssetsByTag(tagName, isFree, pageable)));
    }
}
