package com.expansion.server.domain.notification.dto;

import java.util.List;

/**
 * 알림 목록 커서 페이지(채팅 ChatMessagePage와 동일 패턴).
 * notifications = 최신순, hasMore = 더 과거 알림 존재 여부.
 */
public record NotificationPage(
        List<NotificationResponse> notifications,
        boolean hasMore
) {
}
