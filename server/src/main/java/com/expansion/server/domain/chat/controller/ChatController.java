package com.expansion.server.domain.chat.controller;

import com.expansion.server.domain.chat.dto.ChatEvent;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 커미션 거래룸 채팅 (로그인 필수 — 당사자만, 서비스에서 권한 검증)
@RestController
@RequestMapping("/api/commissions/{commissionId}")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // 메시지 목록 (시간 오름차순)
    @GetMapping("/messages")
    public ApiResponse<Page<ChatMessageResponse>> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @PageableDefault(size = 100, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.ok(chatService.getMessages(commissionId, userId, pageable));
    }

    // 현재 거래룸 접속자 스냅샷 — 입장 직후 초기 presence 상태
    @GetMapping("/presence")
    public ApiResponse<List<Long>> getPresence(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId) {
        return ApiResponse.ok(chatService.getPresence(commissionId, userId));
    }

    // 메시지 전송 — 저장(REST) 후 토픽으로 실시간 브로드캐스트(MESSAGE 봉투)
    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId,
            @Valid @RequestBody ChatMessageCreateRequest request) {
        // 서비스(@Transactional) 반환 시점 = 커밋 완료 → 그 후 브로드캐스트해야 안전
        ChatMessageResponse response = chatService.sendMessage(commissionId, userId, request.getContent());
        messagingTemplate.convertAndSend("/topic/commissions/" + commissionId, ChatEvent.message(response));
        return ApiResponse.ok(response);
    }

    // 읽음 처리 — 상대 메시지를 읽음으로. 새로 읽은 게 있으면 READ 봉투 브로드캐스트(상대가 실시간으로 "읽음" 확인)
    @PostMapping("/messages/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId) {
        Long lastReadMessageId = chatService.markRead(commissionId, userId);
        if (lastReadMessageId != null) {
            messagingTemplate.convertAndSend("/topic/commissions/" + commissionId,
                    ChatEvent.read(userId, lastReadMessageId));
        }
        return ApiResponse.ok("읽음 처리되었습니다.");
    }
}
