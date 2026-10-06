package com.expansion.server.domain.user.repository;

import com.expansion.server.domain.user.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** 가장 최근 발급 토큰 — 재요청 쿨다운 판정용 */
    Optional<PasswordResetToken> findFirstByUser_UserIdOrderByCreatedAtDesc(Long userId);

    /** 재발급·재설정 완료 시 그 사용자의 토큰 정리(이전 링크 무효화) */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    /**
     * 토큰 사용 처리 — 미사용·미만료일 때만 1행 갱신(원자적). 같은 링크로 동시에 두 번 요청해도
     * 한쪽만 1을 받는다(조회 후 표시 방식은 둘 다 통과할 수 있음). 반환 = 갱신 행 수.
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t SET t.usedAt = :now
            WHERE t.tokenId = :tokenId AND t.usedAt IS NULL AND t.expiresAt > :now
            """)
    int markUsedIfValid(@Param("tokenId") Long tokenId, @Param("now") LocalDateTime now);
}
