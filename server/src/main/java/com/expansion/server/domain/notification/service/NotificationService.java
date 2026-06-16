package com.expansion.server.domain.notification.service;

import com.expansion.server.domain.chat.repository.ChatMessageRepository;
import com.expansion.server.domain.notification.dto.NotificationPage;
import com.expansion.server.domain.notification.dto.NotificationResponse;
import com.expansion.server.domain.notification.dto.UnreadCountResponse;
import com.expansion.server.domain.notification.entity.Notification;
import com.expansion.server.domain.notification.event.NotificationEvent;
import com.expansion.server.domain.notification.repository.NotificationRepository;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int TITLE_MAX = 100;
    private static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final ProfileRepository profileRepository;
    private final ChatMessageRepository chatMessageRepository;

    // ── 생성 (이벤트 리스너에서 호출, AFTER_COMMIT/REQUIRES_NEW 트랜잭션 안) ──
    public void create(NotificationEvent e) {
        // 수신자/발신자 누락 또는 자기 자신 대상(내 글에 내가 댓글 등)이면 만들지 않음
        if (e.recipientId() == null || e.senderId() == null || e.recipientId().equals(e.senderId())) {
            return;
        }

        String nickname = profileRepository.findByUser_UserId(e.senderId())
                .map(Profile::getNickname)
                .orElse("알 수 없는 사용자");

        String title = clamp(String.format(e.type().getTitleTemplate(), nickname));

        notificationRepository.save(Notification.builder()
                .userId(e.recipientId())
                .senderId(e.senderId())
                .type(e.type().name())
                .title(title)
                .targetType(e.type().getTargetType())
                .targetId(e.targetId())
                .build());
    }

    // ── 목록 (커서 페이지네이션, unreadOnly=true면 안읽음만) ──
    public NotificationPage getList(Long userId, Long beforeId, int size, boolean unreadOnly) {
        int capped = Math.min(Math.max(size, 1), MAX_SIZE);

        List<Notification> rows = notificationRepository.findPage(
                userId, beforeId, unreadOnly, PageRequest.of(0, capped + 1));

        boolean hasMore = rows.size() > capped;
        if (hasMore) {
            rows = rows.subList(0, capped);
        }

        // sender 프로필 배치 조회 (N+1 방지)
        List<Long> senderIds = rows.stream()
                .map(Notification::getSenderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Profile> profileMap = senderIds.isEmpty() ? Map.of()
                : profileRepository.findAllByUser_UserIdIn(senderIds).stream()
                        .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        List<NotificationResponse> list = rows.stream()
                .map(n -> NotificationResponse.of(n, profileMap.get(n.getSenderId())))
                .toList();

        return new NotificationPage(list, hasMore);
    }

    // ── 안읽음 집계 (알림 + 채팅 합산) ──
    public UnreadCountResponse unreadCount(Long userId) {
        long noti = notificationRepository.countByUserIdAndIsReadFalse(userId);
        long chat = chatMessageRepository.countAllUnreadForUser(userId);
        return new UnreadCountResponse(noti, chat, noti + chat);
    }

    // ── 단건 읽음 ──
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByNotificationIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        n.markRead();
    }

    // ── 전체 읽음 (알림만, 채팅은 채팅 읽음 처리로 차감) ──
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    private String clamp(String s) {
        return s.length() <= TITLE_MAX ? s : s.substring(0, TITLE_MAX);
    }
}
