package com.expansion.server.domain.challenge.repository;

import com.expansion.server.domain.challenge.entity.ChallengeTopic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChallengeTopicRepository extends JpaRepository<ChallengeTopic, Long> {

    /**
     * 다음에 쓸 주제 — ACTIVE 중 안 쓴 것(sort_order 순) 먼저, 다 썼으면 가장 오래전에 쓴 것.
     * 행 잠금으로 동시 생성 대비. (관리자 목록의 '공개 예정 순서'와 같은 정렬)
     */
    @Query(value = """
            SELECT * FROM challenge_topics
            WHERE status = 'ACTIVE'
            ORDER BY used_at ASC NULLS FIRST, sort_order ASC, topic_id ASC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<ChallengeTopic> findNextForUpdate();

    /** 아직 안 쓴 ACTIVE 주제 수 — 풀 보충 기준 */
    long countByUsedAtIsNullAndStatus(String status);

    /** 아직 안 쓴 주제(상태 무관) — AI 중복 회피용 목록 */
    List<ChallengeTopic> findByUsedAtIsNullOrderBySortOrderAscTopicIdAsc();

    /** 최근에 쓴 주제 — AI 중복 회피용 목록 */
    List<ChallengeTopic> findByUsedAtIsNotNullOrderByUsedAtDesc(Pageable pageable);

    @Query("SELECT COALESCE(MAX(t.sortOrder), 0) FROM ChallengeTopic t")
    int findMaxSortOrder();

    /**
     * 주제 추가(AI 보충용) — 같은 주제(띄어쓰기·대소문자 무시, V39 유니크 인덱스)가 있으면 건너뜀(0 반환).
     * 예외 없이 건너뛰므로 한 개가 겹쳐도 나머지는 들어간다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO challenge_topics (topic, description, sort_order, source, status, created_at)
            VALUES (:topic, :description, :sortOrder, :source, 'ACTIVE', :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("topic") String topic, @Param("description") String description,
                       @Param("sortOrder") int sortOrder, @Param("source") String source,
                       @Param("now") LocalDateTime now);

    /** 같은 주제가 이미 있는지(정규화 비교, 자기 자신 제외) — 관리자 추가·수정 전 확인. 식은 V39 인덱스와 동일 */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM challenge_topics
              WHERE lower(regexp_replace(topic, '\\s', '', 'g')) = :normalized
                AND topic_id <> :excludeId)
            """, nativeQuery = true)
    boolean existsNormalized(@Param("normalized") String normalized, @Param("excludeId") long excludeId);

    /**
     * 관리자 목록 — 공개 예정 순서(꺼내는 순서와 동일). 필터는 빈 문자열/'all'이면 전체.
     * used: 'all' | 'used' | 'unused'
     */
    @Query(value = """
            SELECT t FROM ChallengeTopic t
            WHERE (:status = '' OR t.status = :status)
              AND (:source = '' OR t.source = :source)
              AND (:used = 'all' OR (:used = 'used' AND t.usedAt IS NOT NULL) OR (:used = 'unused' AND t.usedAt IS NULL))
            ORDER BY t.usedAt ASC NULLS FIRST, t.sortOrder ASC, t.topicId ASC
            """)
    Page<ChallengeTopic> searchForAdmin(@Param("status") String status, @Param("source") String source,
                                        @Param("used") String used, Pageable pageable);
}
