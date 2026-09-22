package com.expansion.server.domain.ai.controller;

import com.expansion.server.domain.ai.dto.ConceptRequest;
import com.expansion.server.domain.ai.dto.ConceptResponse;
import com.expansion.server.domain.ai.dto.PaletteSuggestRequest;
import com.expansion.server.domain.ai.dto.PaletteSuggestResponse;
import com.expansion.server.domain.ai.dto.TagPaletteRequest;
import com.expansion.server.domain.ai.service.AiService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * AI 기능 엔드포인트 (에디터 AI 탭).
 * 로그인 필수(SecurityConfig의 anyRequest().authenticated()로 커버).
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * 내 작업물 색 추천 — 캔버스 이미지 + 현재 색 + 자연어(선택)를 근거로 어울리는 색 팔레트 제안.
     */
    @PostMapping("/palette-suggest")
    public ApiResponse<PaletteSuggestResponse> suggestPalette(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaletteSuggestRequest request) {
        return ApiResponse.ok(aiService.suggestPalette(userId, request));
    }

    /**
     * 태그로 색 찾기 — 태그/키워드(이미지 없음)로 어울리는 색 팔레트 제안.
     */
    @PostMapping("/palette-by-tags")
    public ApiResponse<PaletteSuggestResponse> suggestPaletteByTags(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TagPaletteRequest request) {
        return ApiResponse.ok(aiService.suggestPaletteByTags(userId, request));
    }

    /**
     * 컨셉 도우미 — 자연어 컨셉 설명으로 색 팔레트 + 우리 갤러리의 관련 작품 추천.
     */
    @PostMapping("/concept")
    public ApiResponse<ConceptResponse> suggestConcept(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ConceptRequest request) {
        return ApiResponse.ok(aiService.suggestConcept(userId, request));
    }
}
