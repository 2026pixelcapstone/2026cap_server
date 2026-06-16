package com.expansion.server.domain.notification.controller;

import com.expansion.server.domain.notification.dto.NotificationPage;
import com.expansion.server.domain.notification.dto.UnreadCountResponse;
import com.expansion.server.domain.notification.service.NotificationService;
import com.expansion.server.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 알림 (로그인 필수 — SecurityConfig anyRequest().authenticated()로 보호).
 * 채팅 안읽음은 별도 row 없이 unread-count의 chat 필드로만 합산 노출한다.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 목록 (커서 — before=null이면 최신부터, before=notificationId면 그보다 이전)
    // unreadOnly=true면 안읽음만 (드롭다운용), 기본 false면 전체 (전체보기 페이지용)
    @GetMapping
    public ApiResponse<NotificationPage> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.ok(notificationService.getList(userId, before, size, unreadOnly));
    }

    // 안읽음 집계 (종 배지 폴링 대상) — {notifications, chat, total}
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(notificationService.unreadCount(userId));
    }

    // 단건 읽음
    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId) {
        notificationService.markRead(userId, notificationId);
        return ApiResponse.ok("읽음 처리되었습니다.");
    }

    // 전체 읽음 (알림만)
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(
            @AuthenticationPrincipal Long userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.ok("모두 읽음 처리되었습니다.");
    }
}
