package com.expansion.server.domain.challenge.controller;

import com.expansion.server.domain.challenge.dto.ChallengeEntryRequest;
import com.expansion.server.domain.challenge.dto.ChallengeEntryResponse;
import com.expansion.server.domain.challenge.dto.ChallengeResponse;
import com.expansion.server.domain.challenge.dto.PastChallengeResponse;
import com.expansion.server.domain.challenge.service.ChallengeService;
import com.expansion.server.domain.gallery.dto.GalleryPostSummary;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** 주간 챌린지(C-2). GET은 공개, 참가·취소는 로그인 필요(SecurityConfig). */
@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    private Long resolveUserId(Long principal) {
        if (principal != null) return principal;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }

    // GET /api/challenges/current — 준비 중이면 data = null
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<ChallengeResponse>> getCurrent(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.getCurrent(resolveUserId(userId))));
    }

    // GET /api/challenges/past?page&size
    @GetMapping("/past")
    public ResponseEntity<ApiResponse<Page<PastChallengeResponse>>> getPast(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.getPast(page, size)));
    }

    // GET /api/challenges/{id}/entries?sort=likes|recent&page&size
    @GetMapping("/{challengeId}/entries")
    public ResponseEntity<ApiResponse<Page<GalleryPostSummary>>> getEntries(
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "likes") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.getEntries(challengeId, sort, page, size)));
    }

    // POST /api/challenges/current/entry {postId} — 참가 또는 교체
    @PostMapping("/current/entry")
    public ResponseEntity<ApiResponse<ChallengeEntryResponse>> enter(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChallengeEntryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.enter(userId, request.postId())));
    }

    // DELETE /api/challenges/current/entry — 참가 취소
    @DeleteMapping("/current/entry")
    public ResponseEntity<ApiResponse<Void>> cancel(@AuthenticationPrincipal Long userId) {
        challengeService.cancel(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
