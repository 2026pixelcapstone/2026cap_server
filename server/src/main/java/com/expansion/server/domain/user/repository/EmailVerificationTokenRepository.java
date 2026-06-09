package com.expansion.server.domain.user.repository;

import com.expansion.server.domain.user.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    // 재발급 시 기존 토큰 정리 (이전 링크 무효화)
    void deleteByUser_UserId(Long userId);
}
