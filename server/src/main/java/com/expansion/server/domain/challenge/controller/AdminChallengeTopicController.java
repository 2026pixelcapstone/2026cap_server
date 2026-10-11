package com.expansion.server.domain.challenge.controller;

import com.expansion.server.domain.challenge.dto.ChallengeTopicRequest;
import com.expansion.server.domain.challenge.dto.ChallengeTopicResponse;
import com.expansion.server.domain.challenge.dto.ChallengeTopicStatusRequest;
import com.expansion.server.domain.challenge.service.ChallengeTopicService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자 — 챌린지 주제 풀 관리(C-2b). 관리자 화면이 생기면 바로 붙여 쓰도록 준비해 둔 API.
 * 권한은 SecurityConfig의 /api/admin/** → hasRole("ADMIN")에 맡긴다(일반 사용자 403, 비로그인 401).
 */
@RestController
@RequestMapping("/api/admin/challenge-topics")
@RequiredArgsConstructor
public class AdminChallengeTopicController {

    private final ChallengeTopicService topicService;

    // GET /api/admin/challenge-topics?status=&source=&used=all|used|unused&page&size — 공개 예정 순서
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ChallengeTopicResponse>>> search(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String source,
            @RequestParam(defaultValue = "all") String used,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(topicService.search(status, source, used, page, size)));
    }

    // POST /api/admin/challenge-topics — 직접 추가(source=ADMIN, 맨 뒤 순서)
    @PostMapping
    public ResponseEntity<ApiResponse<ChallengeTopicResponse>> create(@Valid @RequestBody ChallengeTopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(topicService.create(request.topic(), request.description())));
    }

    // PATCH /api/admin/challenge-topics/{id} — 문구 수정
    @PatchMapping("/{topicId}")
    public ResponseEntity<ApiResponse<ChallengeTopicResponse>> update(
            @PathVariable Long topicId, @Valid @RequestBody ChallengeTopicRequest request) {
        return ResponseEntity.ok(ApiResponse.success(topicService.update(topicId, request.topic(), request.description())));
    }

    // PATCH /api/admin/challenge-topics/{id}/status — 사용 중지·재사용
    @PatchMapping("/{topicId}/status")
    public ResponseEntity<ApiResponse<ChallengeTopicResponse>> changeStatus(
            @PathVariable Long topicId, @Valid @RequestBody ChallengeTopicStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(topicService.changeStatus(topicId, request.status())));
    }

    // POST /api/admin/challenge-topics/replenish — AI로 지금 보충(기준 무관), 추가된 개수 반환
    @PostMapping("/replenish")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> replenish() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("added", topicService.replenishNow())));
    }
}
