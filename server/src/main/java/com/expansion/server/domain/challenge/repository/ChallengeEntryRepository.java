package com.expansion.server.domain.challenge.repository;

import com.expansion.server.domain.challenge.entity.ChallengeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChallengeEntryRepository extends JpaRepository<ChallengeEntry, Long> {

    Optional<ChallengeEntry> findByChallengeIdAndUserId(Long challengeId, Long userId);

    List<ChallengeEntry> findByChallengeIdInAndFinalRankIsNotNull(Collection<Long> challengeIds);

    /**
     * 참가 또는 교체(1인 1작) — 원자적 upsert라 동시 요청에도 UNIQUE 예외가 나지 않는다
     * (작품 등록 트랜잭션 안에서 불리므로 예외로 트랜잭션이 깨지면 안 됨).
     * clearAutomatically는 쓰지 않는다 — 같은 트랜잭션의 새 작품 엔티티가 분리되면 안 되므로.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO challenge_entries (challenge_id, user_id, post_id, created_at, updated_at)
            VALUES (:challengeId, :userId, :postId, :now, :now)
            ON CONFLICT (challenge_id, user_id)
            DO UPDATE SET post_id = EXCLUDED.post_id, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsert(@Param("challengeId") Long challengeId, @Param("userId") Long userId,
               @Param("postId") Long postId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ChallengeEntry e WHERE e.challengeId = :challengeId AND e.userId = :userId")
    int deleteByChallengeIdAndUserId(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    /** 공개 참가작 수(비공개로 바꾼 작품은 제외) */
    @Query(value = """
            SELECT COUNT(*) FROM challenge_entries e
            JOIN gallery_posts p ON p.post_id = e.post_id AND p.visibility = 'PUBLIC'
            WHERE e.challenge_id = :challengeId
            """, nativeQuery = true)
    long countPublicEntries(@Param("challengeId") Long challengeId);

    /** 공개 참가작 — 현재 좋아요순(동률은 최신·id로 고정) */
    @Query(value = """
            SELECT e.post_id FROM challenge_entries e
            JOIN gallery_posts p ON p.post_id = e.post_id AND p.visibility = 'PUBLIC'
            WHERE e.challenge_id = :challengeId
            ORDER BY p.like_count DESC, p.created_at DESC, p.post_id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM challenge_entries e
            JOIN gallery_posts p ON p.post_id = e.post_id AND p.visibility = 'PUBLIC'
            WHERE e.challenge_id = :challengeId
            """, nativeQuery = true)
    Page<Long> findPublicPostIdsByLikes(@Param("challengeId") Long challengeId, Pageable pageable);

    /** 공개 참가작 — 작품 최신순 */
    @Query(value = """
            SELECT e.post_id FROM challenge_entries e
            JOIN gallery_posts p ON p.post_id = e.post_id AND p.visibility = 'PUBLIC'
            WHERE e.challenge_id = :challengeId
            ORDER BY p.created_at DESC, p.post_id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM challenge_entries e
            JOIN gallery_posts p ON p.post_id = e.post_id AND p.visibility = 'PUBLIC'
            WHERE e.challenge_id = :challengeId
            """, nativeQuery = true)
    Page<Long> findPublicPostIdsByRecent(@Param("challengeId") Long challengeId, Pageable pageable);

    /**
     * 판정용 순위 — 공개 참가작별로 '마감 전에 받은 좋아요'만 센다(판정이 늦게 돌아도 마감 뒤 좋아요는 무시).
     * 동률은 작품을 먼저 올린 순 → entry_id. 결과 행 = [entry_id, like_count].
     * GROUP BY는 원시 컬럼만(TROUBLESHOOTING: 식 그룹은 Postgres가 거부).
     */
    @Query(value = """
            SELECT e.entry_id, COUNT(l.like_id)
            FROM challenge_entries e
            JOIN gallery_posts p ON p.post_id = e.post_id AND p.visibility = 'PUBLIC'
            LEFT JOIN likes l ON l.target_type = 'GALLERY_POST' AND l.target_id = e.post_id AND l.created_at < :endsAt
            WHERE e.challenge_id = :challengeId
            GROUP BY e.entry_id, p.created_at
            ORDER BY COUNT(l.like_id) DESC, p.created_at ASC, e.entry_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> rankForJudging(@Param("challengeId") Long challengeId,
                                  @Param("endsAt") LocalDateTime endsAt,
                                  @Param("limit") int limit);
}
