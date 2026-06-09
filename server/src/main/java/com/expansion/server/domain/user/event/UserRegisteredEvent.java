package com.expansion.server.domain.user.event;

/**
 * 회원가입 완료 이벤트. 인증 메일 발송을 회원가입 트랜잭션 커밋 이후로 분리하기 위해 사용
 * (롤백 시 무효 링크가 발송되는 것을 방지).
 */
public record UserRegisteredEvent(Long userId) {
}
