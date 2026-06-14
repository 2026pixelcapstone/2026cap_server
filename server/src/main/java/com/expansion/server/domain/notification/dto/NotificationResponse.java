package com.expansion.server.domain.notification.dto;

import com.expansion.server.domain.notification.entity.Notification;
import com.expansion.server.domain.user.entity.Profile;

import java.time.LocalDateTime;

/**
 * 알림 목록 항목. senderNickname/senderProfileImageUrl은 sender 프로필 배치 조회로 채운다
 * (프로필이 없으면 null — 탈퇴 등).
 */
public record NotificationResponse(
        Long notificationId,
        String type,
        String title,
        String targetType,
        Long targetId,
        boolean isRead,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        LocalDateTime createdAt
) {
    public static NotificationResponse of(Notification n, Profile senderProfile) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getType(),
                n.getTitle(),
                n.getTargetType(),
                n.getTargetId(),
                n.isRead(),
                n.getSenderId(),
                senderProfile != null ? senderProfile.getNickname() : null,
                senderProfile != null ? senderProfile.getProfileImageUrl() : null,
                n.getCreatedAt()
        );
    }
}
