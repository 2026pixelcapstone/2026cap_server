package com.expansion.server.domain.gallery.controller;

import com.expansion.server.domain.gallery.dto.*;
import com.expansion.server.domain.gallery.service.GalleryService;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    /** @AuthenticationPrincipal이 null을 반환하는 경우 SecurityContext에서 직접 추출 */
    private Long resolveUserId(Long principal) {
        if (principal != null) return principal;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }

    /** 로그인 필수 엔드포인트용 — null이면 예외 발생 */
    private Long requireUserId(Long principal) {
        Long id = resolveUserId(principal);
        if (id == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return id;
    }

    // ──────────────────────────────────────────────
    // 게시물 목록 조회 (비로그인 허용)
    // GET /api/gallery?type=FREE&page=0&size=20&sort=createdAt,desc
    // ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<GalleryPostSummary>>> getPostList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long likedBy,
            @AuthenticationPrincipal Long currentUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Long resolvedUserId = resolveUserId(currentUserId);

        // likedBy와 authorId 동시 사용 금지
        if (likedBy != null && authorId != null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("likedBy와 authorId는 동시에 사용할 수 없습니다."));
        }

        // 유효하지 않은 galleryType 거부
        if (type != null && !type.equals("FREE") && !type.equals("DEDICATED")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("type은 FREE 또는 DEDICATED만 허용됩니다."));
        }

        // likedBy 있으면 해당 유저가 좋아요한 게시물 반환
        if (likedBy != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    galleryService.getLikedPosts(likedBy, pageable)));
        }

        // authorId 있으면 특정 유저의 게시물 반환 (마이페이지/프로필 페이지용)
        if (authorId != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    galleryService.getUserPosts(authorId, resolvedUserId, pageable)));
        }

        String galleryType = (type != null) ? type : "FREE";
        return ResponseEntity.ok(ApiResponse.success(galleryService.getPostList(galleryType, pageable)));
    }

    // ──────────────────────────────────────────────
    // 여러 작가의 대표작(포트폴리오) 일괄 조회 (비로그인 허용)
    // GET /api/gallery/portfolios?authorIds=1,2,3&perAuthor=3
    // → { "1": [summary...], "2": [...] }  (카드 목록 N+1 방지용 배치 API)
    // ──────────────────────────────────────────────

    @GetMapping("/portfolios")
    public ResponseEntity<ApiResponse<Map<Long, List<GalleryPostSummary>>>> getPortfolios(
            @RequestParam List<Long> authorIds,
            @RequestParam(defaultValue = "3") int perAuthor) {

        return ResponseEntity.ok(ApiResponse.success(
                galleryService.getPortfolios(authorIds, perAuthor)));
    }

    // ──────────────────────────────────────────────
    // 게시물 상세 조회 (비로그인 허용) — 조회수 증가 없음
    // GET /api/gallery/{postId}
    // ──────────────────────────────────────────────

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<GalleryPostResponse>> getPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal Long currentUserId) {

        return ResponseEntity.ok(ApiResponse.success(
                galleryService.getPost(postId, resolveUserId(currentUserId))));
    }

    // ──────────────────────────────────────────────
    // 조회수 증가 (비로그인 허용) — 프론트에서 별도 호출
    // POST /api/gallery/{postId}/view
    // ──────────────────────────────────────────────

    @PostMapping("/{postId}/view")
    public ResponseEntity<ApiResponse<Void>> incrementView(
            @PathVariable Long postId) {

        galleryService.incrementView(postId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ──────────────────────────────────────────────
    // 게시물 등록 (로그인 필수)
    // POST /api/gallery
    // ──────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<GalleryPostResponse>> createPost(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GalleryPostCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(galleryService.createPost(requireUserId(userId), request)));
    }

    // ──────────────────────────────────────────────
    // 게시물 수정 (로그인 + 작성자)
    // PATCH /api/gallery/{postId}
    // ──────────────────────────────────────────────

    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<GalleryPostResponse>> updatePost(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId,
            @Valid @RequestBody GalleryPostUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                galleryService.updatePost(requireUserId(userId), postId, request)));
    }

    // ──────────────────────────────────────────────
    // 게시물 삭제 (로그인 + 작성자)
    // DELETE /api/gallery/{postId}
    // ──────────────────────────────────────────────

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId) {

        galleryService.deletePost(requireUserId(userId), postId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ──────────────────────────────────────────────
    // 좋아요 토글 (로그인 필수)
    // POST /api/gallery/{postId}/like
    // ──────────────────────────────────────────────

    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId) {

        boolean liked = galleryService.toggleLike(requireUserId(userId), postId);
        return ResponseEntity.ok(ApiResponse.success(liked));
    }

    // ──────────────────────────────────────────────
    // 댓글 목록 조회 (비로그인 허용)
    // GET /api/gallery/{postId}/comments
    // ──────────────────────────────────────────────

    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<Page<GalleryCommentResponse>>> getComments(
            @PathVariable Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                galleryService.getComments(postId, pageable)));
    }

    // ──────────────────────────────────────────────
    // 댓글 작성 (로그인 필수)
    // POST /api/gallery/{postId}/comments
    // ──────────────────────────────────────────────

    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<GalleryCommentResponse>> createComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId,
            @Valid @RequestBody GalleryCommentCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        galleryService.createComment(requireUserId(userId), postId, request)));
    }

    // ──────────────────────────────────────────────
    // 댓글 삭제 (로그인 + 작성자)
    // DELETE /api/gallery/{postId}/comments/{commentId}
    // ──────────────────────────────────────────────

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long postId,
            @PathVariable Long commentId) {

        galleryService.deleteComment(requireUserId(userId), postId, commentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ──────────────────────────────────────────────
    // 검색 (비로그인 허용)
    // GET /api/gallery/search?keyword=xxx
    // ──────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<GalleryPostSummary>>> search(
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                galleryService.searchPosts(keyword, pageable)));
    }

    // ──────────────────────────────────────────────
    // 태그별 조회 (비로그인 허용)
    // GET /api/gallery/tags/{tagName}
    // ──────────────────────────────────────────────

    @GetMapping("/tags/{tagName}")
    public ResponseEntity<ApiResponse<Page<GalleryPostSummary>>> getByTag(
            @PathVariable String tagName,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        if (type != null && !type.equals("FREE") && !type.equals("DEDICATED")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("type은 FREE 또는 DEDICATED만 허용됩니다."));
        }

        return ResponseEntity.ok(ApiResponse.success(
                galleryService.getPostsByTag(tagName, type, pageable)));
    }
}
