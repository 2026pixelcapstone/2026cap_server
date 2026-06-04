package com.expansion.server.domain.chat.controller;

import com.expansion.server.domain.chat.dto.ChatMessageCreateRequest;
import com.expansion.server.domain.chat.dto.ChatMessageResponse;
import com.expansion.server.domain.chat.service.ChatService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 커미션 거래룸 채팅 (로그인 필수 — 당사자만, 서비스에서 권한 검증)
@RestController
@RequestMapping("/api/commissions/{commissionId}/messages")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 메시지 목록 (시간 오름차순)
    @GetMapping
    public ApiResponse<Page<ChatMessageResponse>> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @PageableDefault(size = 100, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.ok(chatService.getMessages(commissionId, userId, pageable));
    }

    // 메시지 전송
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @Valid @RequestBody ChatMessageCreateRequest request) {
        return ApiResponse.ok(chatService.sendMessage(commissionId, userId, request.getContent()));
    }
}
