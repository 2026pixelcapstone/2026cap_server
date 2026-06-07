package com.expansion.server.domain.chat.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 거래룸 접속(presence) 상태 추적기 — 인메모리(단일 서버 인스턴스 전제).
 * 세션 단위로 추적해 같은 사용자가 여러 탭을 열어도 정확히 동작.
 */
@Component
public class ChatPresenceTracker {

    // commissionId -> (sessionId -> userId)
    private final Map<Long, Map<String, Long>> roomSessions = new ConcurrentHashMap<>();
    // sessionId -> commissionId (퇴장 시 어느 방인지 역추적)
    private final Map<String, Long> sessionRoom = new ConcurrentHashMap<>();

    public void join(String sessionId, Long commissionId, Long userId) {
        roomSessions.computeIfAbsent(commissionId, k -> new ConcurrentHashMap<>()).put(sessionId, userId);
        sessionRoom.put(sessionId, commissionId);
    }

    /** 세션 제거 후 영향받은 commissionId 반환(추적 중이던 세션이 아니면 null) */
    public Long leave(String sessionId) {
        Long commissionId = sessionRoom.remove(sessionId);
        if (commissionId == null) return null;
        Map<String, Long> sessions = roomSessions.get(commissionId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) roomSessions.remove(commissionId);
        }
        return commissionId;
    }

    /** 현재 방에 있는 사용자 ID 목록(중복 제거 — 멀티탭은 1명으로) */
    public List<Long> getPresent(Long commissionId) {
        Map<String, Long> sessions = roomSessions.get(commissionId);
        if (sessions == null) return List.of();
        return sessions.values().stream().distinct().toList();
    }
}
