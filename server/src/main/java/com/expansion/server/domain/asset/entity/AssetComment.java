package com.expansion.server.domain.asset.entity;

import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "asset_comments")
@Getter
@NoArgsConstructor
public class AssetComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private AssetComment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<AssetComment> replies = new ArrayList<>();

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // 리뷰 별점(1~5). null이면 일반 댓글(별점 없음). 최상위 리뷰에만 부여.
    // DB 컬럼은 SMALLINT → Java Integer 유지하되 JDBC 타입을 SMALLINT로 매핑(스키마 검증 일치)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column
    private Integer rating;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AssetComment(Asset asset, User user, AssetComment parent, String content, Integer rating) {
        this.asset = asset;
        this.user = user;
        this.parent = parent;
        this.content = content;
        this.rating = rating;
        this.isDeleted = false;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    // 리뷰 재등록(갱신) — 내용·별점 교체
    public void updateReview(String content, Integer rating) {
        this.content = content;
        this.rating = rating;
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.isDeleted = true;
        this.updatedAt = LocalDateTime.now();
    }
}
