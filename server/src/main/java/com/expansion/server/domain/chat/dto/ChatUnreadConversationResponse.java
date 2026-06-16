package com.expansion.server.domain.chat.dto;

import java.time.LocalDateTime;

/**
 * 알림 채팅 미리보기 항목 — 안읽은 메시지가 있는 거래방 하나.
 * lastMessageContent/At = 그 방의 가장 최신 메시지(읽음 무관). 클릭 시 commissionId로 거래룸 이동.
 */
public record ChatUnreadConversationResponse(
        Long commissionId,
        Long partnerId,
        String partnerNickname,
        String partnerProfileImageUrl,
        String lastMessageContent,
        LocalDateTime lastMessageAt,
        long unreadCount
) {
}
