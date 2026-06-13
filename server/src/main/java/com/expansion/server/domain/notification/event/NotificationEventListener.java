package com.expansion.server.domain.notification.event;

import com.expansion.server.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 생성 리스너. 본 비즈니스 트랜잭션 커밋 이후(AFTER_COMMIT)에만,
 * 별도 트랜잭션(REQUIRES_NEW)에서 알림을 저장한다(가입 메일과 동일 패턴).
 * 알림 저장 중 예외는 삼켜서 사용자 본 동작에 영향을 주지 않는다(베스트 에포트).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotification(NotificationEvent event) {
        try {
            notificationService.create(event);
        } catch (Exception e) {
            log.warn("[NOTI] 알림 생성 실패 — type={}, recipient={}, sender={}",
                    event.type(), event.recipientId(), event.senderId(), e);
        }
    }
}
