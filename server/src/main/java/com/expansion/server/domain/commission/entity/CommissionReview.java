package com.expansion.server.domain.commission.entity;

import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 커미션 평점/리뷰 — 완료된 거래에 대해 의뢰자가 작가를 평가한다(거래당 1리뷰).
 * artist_id는 작가별 집계(GROUP BY)를 위해 비정규화 저장.
 */
@Entity
@Table(name = "commission_reviews")
@Getter
@NoArgsConstructor
public class CommissionReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    // 거래당 1리뷰 (UNIQUE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_id", nullable = false, unique = true)
    private Commission commission;

    // 리뷰 작성자 = 의뢰자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    // 평가 대상 = 작가 (집계 편의를 위해 비정규화)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    // DB 컬럼은 SMALLINT → Java Integer 유지하되 JDBC 타입을 SMALLINT로 매핑(스키마 검증 일치)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public CommissionReview(Commission commission, User reviewer, User artist, Integer rating, String content) {
        this.commission = commission;
        this.reviewer = reviewer;
        this.artist = artist;
        this.rating = rating;
        this.content = content;
    }

    /** 재작성(수정) — 별점/내용만 갱신 */
    public void update(Integer rating, String content) {
        this.rating = rating;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
