package com.expansion.server.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 주간 챌린지(V38). 기간은 [startsAt, endsAt) — endsAt은 다음 주 월 00:00 KST(미포함).
 * 주제 문구는 풀에서 복사해 둔 값. judgedAt != null 이면 판정 끝(winnerCount = 순위 매긴 수).
 */
@Entity
@Table(name = "challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_id")
    private Long challengeId;

    @Column(nullable = false, length = 50)
    private String topic;

    @Column(length = 300)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "judged_at")
    private LocalDateTime judgedAt;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "winner_count", nullable = false)
    private Integer winnerCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Challenge(String topic, String description, LocalDateTime startsAt, LocalDateTime endsAt) {
        this.topic = topic;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.winnerCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isJudged() {
        return judgedAt != null;
    }

    public void markJudged(int winnerCount, LocalDateTime now) {
        this.winnerCount = winnerCount;
        this.judgedAt = now;
    }
}
