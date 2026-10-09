package com.expansion.server.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 챌린지 참가(V38). 1인 1작 — 교체는 postId만 바뀐다(저장은 리포지토리의 원자적 upsert).
 * userId/postId는 raw FK로 보관하고 작품 목록은 id로 일괄 조회한다.
 */
@Entity
@Table(name = "challenge_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long entryId;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "final_rank")
    private Integer finalRank;

    @Column(name = "final_like_count")
    private Integer finalLikeCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void markRank(int rank, int likeCount) {
        this.finalRank = rank;
        this.finalLikeCount = likeCount;
    }
}
