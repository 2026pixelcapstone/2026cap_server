package com.expansion.server.domain.canvas.controller;

import com.expansion.server.domain.canvas.dto.CanvasRevertRequest;
import com.expansion.server.domain.canvas.service.SharedCanvasService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 관리자 — 공동 캔버스 되돌리기(화면 없이 API). 권한은 SecurityConfig /api/admin/** → ADMIN. */
@RestController
@RequestMapping("/api/admin/canvas")
@RequiredArgsConstructor
public class AdminCanvasController {

    private final SharedCanvasService canvasService;

    // POST /api/admin/canvas/revert {userId} — 이번 시즌의 그 사용자 찍기를 빼고 판을 다시 만듦
    @PostMapping("/revert")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revert(@Valid @RequestBody CanvasRevertRequest req) {
        return ResponseEntity.ok(ApiResponse.success(canvasService.revertUser(req.userId())));
    }
}
