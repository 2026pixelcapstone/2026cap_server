package com.expansion.server.global.websocket;

import com.expansion.server.domain.chat.dto.ChatEvent;
import com.expansion.server.domain.chat.service.ChatPresenceTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

/**
 * STOMP 세션 이벤트로 거래룸 접속(presence)을 추적.
 * - SUBSCRIBE(/topic/commissions/{id}) = 입장 → 추적 + 현재 멤버 브로드캐스트
 * - DISCONNECT = 퇴장 → 제거 + 현재 멤버 브로드캐스트
 * (입장자 본인은 브로드캐스트 타이밍 race가 있을 수 있어, 접속 직후 REST 스냅샷으로 초기 상태를 받음)
 */
@Component
@RequiredArgsConstructor
public class ChatPresenceListener {

    private final ChatPresenceTracker tracker;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String COMMISSION_TOPIC_PREFIX = "/topic/commissions/";

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Principal user = accessor.getUser();
        if (destination == null || user == null || !destination.startsWith(COMMISSION_TOPIC_PREFIX)) return;

        Long commissionId = parseCommissionId(destination);
        if (commissionId == null) return;

        Long userId = parseUserId(user.getName());
        if (userId == null) return;

        tracker.join(accessor.getSessionId(), commissionId, userId);
        broadcast(commissionId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long commissionId = tracker.leave(event.getSessionId());
        if (commissionId != null) broadcast(commissionId);
    }

    private void broadcast(Long commissionId) {
        messagingTemplate.convertAndSend(
                COMMISSION_TOPIC_PREFIX + commissionId,
                ChatEvent.presence(tracker.getPresent(commissionId)));
    }

    private Long parseCommissionId(String destination) {
        try {
            return Long.valueOf(destination.substring(COMMISSION_TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Principal name(userId) 방어적 파싱 — 비정상 값이면 null 반환해 해당 이벤트만 건너뜀
    private Long parseUserId(String name) {
        try {
            return Long.valueOf(name);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
