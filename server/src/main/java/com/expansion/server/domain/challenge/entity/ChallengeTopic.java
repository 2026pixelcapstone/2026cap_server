package com.expansion.server.domain.challenge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 챌린지 주제 풀(V38, V39). 스케줄러가 ACTIVE 주제를 꺼내 쓰면 used_at을 기록한다.
 * 문구를 고쳐도 이미 연 챌린지는 바뀌지 않는다(챌린지 행에 복사해 둠).
 */
@Entity
@Table(name = "challenge_topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeTopic {

    public static final String SOURCE_SEED = "SEED";
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

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

    @Column(nullable = false, length = 10)
    private String source;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ChallengeTopic(String topic, String description, int sortOrder, String source) {
        this.topic = topic;
        this.description = description;
        this.sortOrder = sortOrder;
        this.source = source;
        this.status = STATUS_ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }

    public void edit(String topic, String description) {
        this.topic = topic;
        this.description = description;
    }

    public void changeStatus(String status) {
        this.status = status;
    }
}
