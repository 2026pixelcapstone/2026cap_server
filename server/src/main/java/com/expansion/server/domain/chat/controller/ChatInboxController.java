package com.expansion.server.domain.chat.controller;

import com.expansion.server.domain.chat.dto.ChatUnreadConversationResponse;
import com.expansion.server.domain.chat.service.ChatService;
import com.expansion.server.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 커미션 단위가 아닌, 사용자 전체에 걸친 채팅 조회 (로그인 필수).
 * 알림 드롭다운/전체보기의 채팅 미리보기에 사용.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatInboxController {

    private final ChatService chatService;

    // 안읽은 메시지가 있는 거래방 미리보기 목록 (최신 메시지순)
    @GetMapping("/unread-conversations")
    public ApiResponse<List<ChatUnreadConversationResponse>> getUnreadConversations(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(chatService.getUnreadConversations(userId));
    }
}
