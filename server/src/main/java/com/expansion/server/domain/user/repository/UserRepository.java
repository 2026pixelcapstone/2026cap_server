package com.expansion.server.domain.user.repository;

import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 사용자 행 잠금 조회 — 같은 사용자의 동시 요청을 한 줄로 세움(인증 메일 재발송 횟수 판정 등) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findBySocialIdAndSocialProvider(String socialId, String socialProvider);

    boolean existsByEmail(String email);
}
