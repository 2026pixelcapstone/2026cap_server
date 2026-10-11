package com.expansion.server.domain.notification.event;

import com.expansion.server.domain.notification.entity.NotificationType;

/**
 * 알림 생성 요청 이벤트. 각 도메인 서비스가 트랜잭션 안에서 발행하면
 * NotificationEventListener가 커밋 이후(AFTER_COMMIT) 별도 트랜잭션에서 알림을 저장한다.
 * → 알림 저장 실패가 본 비즈니스(댓글/팔로우/커미션)를 롤백시키지 않는다.
 *
 * @param recipientId 수신자 userId
 * @param senderId    행동 주체 userId (제목의 닉네임 출처). 시스템 알림이면 null
 * @param type        알림 종류
 * @param targetId    클릭 시 이동 대상 ID (type.targetType과 함께 사용)
 * @param title       시스템 알림의 완성된 제목(사용자 알림이면 null — 템플릿+닉네임으로 만듦)
 */
public record NotificationEvent(Long recipientId, Long senderId, NotificationType type, Long targetId, String title) {

    public static NotificationEvent of(Long recipientId, Long senderId, NotificationType type, Long targetId) {
        return new NotificationEvent(recipientId, senderId, type, targetId, null);
    }

    /** 보낸 사람 없는 시스템 알림(예: 챌린지 우승) — 제목을 직접 넘긴다 */
    public static NotificationEvent system(Long recipientId, NotificationType type, Long targetId, String title) {
        return new NotificationEvent(recipientId, null, type, targetId, title);
    }

    public boolean isSystem() {
        return title != null;
    }
}
