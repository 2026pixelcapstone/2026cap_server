package com.expansion.server.domain.user.event;

import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.domain.user.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * 회원가입 커밋 이후 인증 메일을 발송. AFTER_COMMIT 이므로 회원/토큰 생성이 확정된 뒤에만 실행 →
 * 가입이 롤백돼도 무효 링크가 나가지 않는다. 토큰 발급은 별도 트랜잭션(REQUIRES_NEW)에서 수행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEventListener {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRegistered(UserRegisteredEvent event) {
        userRepository.findById(event.userId()).ifPresentOrElse(
                emailVerificationService::issueAndSend,
                () -> log.warn("[MAIL] 가입 이벤트 처리 중 사용자 없음 — userId={}", event.userId()));
    }
}
