package com.expansion.server.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림. V7 notifications 테이블과 1:1 매핑.
 * user_id = 수신자, sender_id = 행동 주체(댓글 작성자/팔로워/커미션 상대). 둘 다 raw FK(Long)로 보관 —
 * 목록 조회 시 sender 프로필을 배치로 한 번에 불러와 N+1을 피한다(서비스 레이어).
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;          // 수신자

    @Column(name = "sender_id")
    private Long senderId;        // 행동 주체

    @Column(nullable = false, length = 50)
    private String type;          // NotificationType.name()

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_type", length = 20)
    private String targetType;    // GALLERY / ASSET / USER / COMMISSION / REQUEST_POST

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @Builder
    private Notification(Long userId, Long senderId, String type, String title,
                         String message, Long targetId, String targetType) {
        this.userId = userId;
        this.senderId = senderId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.targetId = targetId;
        this.targetType = targetType;
    }

    public void markRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }
}
