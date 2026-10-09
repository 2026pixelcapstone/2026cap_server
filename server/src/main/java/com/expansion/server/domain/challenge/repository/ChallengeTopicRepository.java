package com.expansion.server.domain.challenge.repository;

import com.expansion.server.domain.challenge.entity.ChallengeTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ChallengeTopicRepository extends JpaRepository<ChallengeTopic, Long> {

    /** 다음에 쓸 주제 — 안 쓴 것(sort_order 순) 먼저, 다 썼으면 가장 오래전에 쓴 것. 행 잠금으로 동시 생성 대비 */
    @Query(value = """
            SELECT * FROM challenge_topics
            ORDER BY used_at ASC NULLS FIRST, sort_order ASC, topic_id ASC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<ChallengeTopic> findNextForUpdate();
}
