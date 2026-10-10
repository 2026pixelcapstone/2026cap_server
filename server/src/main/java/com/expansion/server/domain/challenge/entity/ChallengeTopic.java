package com.expansion.server.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 챌린지 주제 풀(V38). 스케줄러가 꺼내 쓰면 used_at을 기록한다. */
@Entity
@Table(name = "challenge_topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "topic_id")
    private Long topicId;

    @Column(nullable = false, length = 50)
    private String topic;

    @Column(length = 300)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }
}
