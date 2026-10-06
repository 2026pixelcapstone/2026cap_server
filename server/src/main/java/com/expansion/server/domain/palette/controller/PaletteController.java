package com.expansion.server.domain.palette.controller;

import com.expansion.server.domain.palette.dto.PaletteRequest;
import com.expansion.server.domain.palette.dto.PaletteResponse;
import com.expansion.server.domain.palette.dto.PaletteSummaryResponse;
import com.expansion.server.domain.palette.service.PaletteService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** 커뮤니티 팔레트 공유(C-1). GET은 공개, 나머지는 로그인 필요(SecurityConfig). */
@RestController
@RequestMapping("/api/palettes")
@RequiredArgsConstructor
public class PaletteController {

    private final PaletteService paletteService;

    private Long resolveUserId(Long principal) {
        if (principal != null) return principal;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }

    // GET /api/palettes?sort=popular|recent&minColors&maxColors&keyword&authorId&page&size
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaletteSummaryResponse>>> search(
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(required = false) Integer minColors,
            @RequestParam(required = false) Integer maxColors,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                paletteService.search(sort, minColors, maxColors, keyword, authorId, page, size)));
    }

    // GET /api/palettes/{id}
    @GetMapping("/{paletteId}")
    public ResponseEntity<ApiResponse<PaletteResponse>> get(
            @PathVariable Long paletteId,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(paletteService.get(paletteId, resolveUserId(userId))));
    }

    // POST /api/palettes
    @PostMapping
    public ResponseEntity<ApiResponse<PaletteResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaletteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(paletteService.create(userId, request)));
    }

    // PATCH /api/palettes/{id}
    @PatchMapping("/{paletteId}")
    public ResponseEntity<ApiResponse<PaletteResponse>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paletteId,
            @Valid @RequestBody PaletteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paletteService.update(userId, paletteId, request)));
    }

    // DELETE /api/palettes/{id}
    @DeleteMapping("/{paletteId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paletteId) {
        paletteService.delete(userId, paletteId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // POST /api/palettes/{id}/like — 토글, 반환 = 토글 후 상태
    @PostMapping("/{paletteId}/like")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paletteId) {
        return ResponseEntity.ok(ApiResponse.success(paletteService.toggleLike(userId, paletteId)));
    }
}
