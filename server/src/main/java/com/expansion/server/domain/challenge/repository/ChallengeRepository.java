package com.expansion.server.domain.challenge.repository;

import com.expansion.server.domain.challenge.entity.Challenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    Optional<Challenge> findByStartsAt(LocalDateTime startsAt);

    /** 마감됐는데 아직 판정 안 된 챌린지(오래된 것부터) */
    @Query("SELECT c.challengeId FROM Challenge c WHERE c.judgedAt IS NULL AND c.endsAt <= :now ORDER BY c.startsAt ASC")
    List<Long> findIdsDueForJudging(@Param("now") LocalDateTime now);

    /** 판정용 행 잠금 — 스케줄러와 기동 시 보충이 겹쳐도 한 번만 판정 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Challenge c WHERE c.challengeId = :id")
    Optional<Challenge> findByIdForUpdate(@Param("id") Long id);

    /** 판정 끝난 지난 챌린지(최신 주부터, starts_at은 UNIQUE라 순서 고정) */
    Page<Challenge> findByJudgedAtIsNotNullOrderByStartsAtDesc(Pageable pageable);
}
