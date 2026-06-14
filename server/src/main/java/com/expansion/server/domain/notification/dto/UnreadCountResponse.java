package com.expansion.server.domain.notification.dto;

/**
 * 종 배지용 안읽음 집계. 알림(notifications)과 채팅 안읽음(chat)을 분리해 내려주고
 * total = 둘의 합. 프론트는 보통 total만 배지에 쓰고, 드롭다운 상단 "읽지 않은 메시지 N개"에 chat을 쓴다.
 */
public record UnreadCountResponse(
        long notifications,
        long chat,
        long total
) {
}
