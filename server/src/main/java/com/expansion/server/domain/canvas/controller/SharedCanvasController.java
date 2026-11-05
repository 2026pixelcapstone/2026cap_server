package com.expansion.server.domain.canvas.controller;

import com.expansion.server.domain.canvas.dto.CanvasPixelRequest;
import com.expansion.server.domain.canvas.dto.CanvasPixelResponse;
import com.expansion.server.domain.canvas.dto.CanvasSnapshotResponse;
import com.expansion.server.domain.canvas.service.SharedCanvasService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** 공동 픽셀 캔버스(C-3). GET은 공개(비로그인은 스냅샷만), 찍기는 로그인 + 이메일 인증. */
@RestController
@RequestMapping("/api/canvas")
@RequiredArgsConstructor
public class SharedCanvasController {

    private final SharedCanvasService canvasService;

    private Long resolveUserId(Long principal) {
        if (principal != null) return principal;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }

    // GET /api/canvas — 판 스냅샷 + 팔레트 + 내 남은 쿨다운
    @GetMapping
    public ResponseEntity<ApiResponse<CanvasSnapshotResponse>> snapshot(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(canvasService.snapshot(resolveUserId(userId))));
    }

    // POST /api/canvas/pixels {x, y, color}
    @PostMapping("/pixels")
    public ResponseEntity<ApiResponse<CanvasPixelResponse>> place(@AuthenticationPrincipal Long userId,
                                                                  @Valid @RequestBody CanvasPixelRequest req) {
        return ResponseEntity.ok(ApiResponse.success(canvasService.place(userId, req)));
    }
}
