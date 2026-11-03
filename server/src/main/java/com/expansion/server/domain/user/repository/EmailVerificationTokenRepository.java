package com.expansion.server.domain.user.repository;

import com.expansion.server.domain.user.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * 재발급 시 아직 안 쓴 이전 토큰을 '사용 끝'으로 표시(이전 링크 무효화).
     * 지우지 않고 남기는 이유: 발송 횟수(쿨다운·하루 상한)를 이 행들로 센다.
     * flushAutomatically — 같은 트랜잭션의 대기 변경을 먼저 반영(TROUBLESHOOTING #40)
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE EmailVerificationToken t SET t.usedAt = :now WHERE t.user.userId = :userId AND t.usedAt IS NULL")
    int invalidateUnused(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 가장 최근 발송 — 재발송 쿨다운 판단 */
    Optional<EmailVerificationToken> findFirstByUser_UserIdOrderByCreatedAtDesc(Long userId);

    /** 기간 안 발송 수 — 하루 상한 판단 */
    long countByUser_UserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
