package com.expansion.server.domain.user.controller;

import com.expansion.server.domain.user.dto.ProfileUpdateRequest;
import com.expansion.server.domain.user.dto.UserProfileResponse;
import com.expansion.server.domain.user.service.UserService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private Long resolveUserId(Long principal) {
        if (principal != null) return principal;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }

    /**
     * GET /api/users/me
     * 내 프로필 조회 (로그인 필수)
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyProfile(userId)));
    }

    /**
     * PATCH /api/users/me
     * 내 프로필 수정 (로그인 필수)
     */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProfileUpdateRequest request) {

        // Spring Security 7에서 @AuthenticationPrincipal이 null 반환하는 경우 대비 fallback
        Long resolvedId = userId;
        if (resolvedId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Object principal = auth != null ? auth.getPrincipal() : null;
            log.warn("@AuthenticationPrincipal returned null — SecurityContext auth present={}, principal type={}",
                    auth != null, principal != null ? principal.getClass().getSimpleName() : "null");
            if (auth != null && auth.getPrincipal() instanceof Long id) {
                resolvedId = id;
            }
        }

        if (resolvedId == null) {
            log.error("PATCH /api/users/me: userId를 확인할 수 없습니다. 인증 토큰을 확인하세요.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail("인증이 필요합니다."));
        }

        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(resolvedId, request)));
    }

    /**
     * POST /api/users/me/profile-image (multipart: file)
     * 프로필 이미지 업로드/교체 — PNG·JPEG·GIF·WebP, 최대 2MB. 저장 경로는 서버가 결정.
     */
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserProfileResponse>> uploadProfileImage(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        Long resolvedId = resolveUserId(userId);
        if (resolvedId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("인증이 필요합니다."));
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.uploadProfileImage(resolvedId, file)));
    }

    /**
     * DELETE /api/users/me/profile-image
     * 프로필 이미지 제거(기본 아바타로)
     */
    @DeleteMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<UserProfileResponse>> removeProfileImage(
            @AuthenticationPrincipal Long userId) {
        Long resolvedId = resolveUserId(userId);
        if (resolvedId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("인증이 필요합니다."));
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.removeProfileImage(resolvedId)));
    }

    /**
     * GET /api/users/by-nickname/{nickname}
     * 닉네임으로 유저 프로필 조회 (프로필 페이지용)
     */
    @GetMapping("/by-nickname/{nickname}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserByNickname(
            @PathVariable String nickname,
            @AuthenticationPrincipal Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserByNickname(nickname, resolveUserId(currentUserId))));
    }

    /**
     * GET /api/users/{userId}
     * 특정 유저 프로필 조회
     * - 로그인 상태면 isFollowing 포함
     * - 비로그인 상태면 isFollowing = false
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable Long userId,
            @AuthenticationPrincipal Long currentUserId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserProfile(userId, resolveUserId(currentUserId))));
    }

    /**
     * POST /api/users/{userId}/follow
     * 팔로우 (로그인 필수)
     */
    @PostMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(
            @PathVariable Long userId,
            @AuthenticationPrincipal Long currentUserId) {
        Long resolvedId = resolveUserId(currentUserId);
        if (resolvedId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("인증이 필요합니다."));
        userService.follow(resolvedId, userId);
        return ResponseEntity.ok(ApiResponse.ok("팔로우 했습니다."));
    }

    /**
     * DELETE /api/users/{userId}/follow
     * 언팔로우 (로그인 필수)
     */
    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @PathVariable Long userId,
            @AuthenticationPrincipal Long currentUserId) {
        Long resolvedId = resolveUserId(currentUserId);
        if (resolvedId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("인증이 필요합니다."));
        userService.unfollow(resolvedId, userId);
        return ResponseEntity.ok(ApiResponse.ok("언팔로우 했습니다."));
    }

    /**
     * GET /api/users/{userId}/followers
     * 팔로워 목록 조회
     */
    @GetMapping("/{userId}/followers")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getFollowers(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getFollowers(userId)));
    }

    /**
     * GET /api/users/{userId}/following
     * 팔로잉 목록 조회
     */
    @GetMapping("/{userId}/following")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getFollowing(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getFollowing(userId)));
    }
}
