package com.expansion.server.domain.asset.entity;

import com.expansion.server.domain.common.entity.Category;
import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_id")
    private Long assetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "license_type_id")
    private AssetLicenseType licenseType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "is_free", nullable = false)
    private boolean isFree;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "palette_data", columnDefinition = "jsonb")
    private String paletteData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<AssetImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssetTag> assetTags = new ArrayList<>();

    @Builder
    public Asset(User user, Category category, AssetLicenseType licenseType,
                 String title, String description, String thumbnailUrl,
                 BigDecimal price, boolean isFree, String paletteData) {
        this.user = user;
        this.category = category;
        this.licenseType = licenseType;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.price = price != null ? price : BigDecimal.ZERO;
        this.isFree = isFree;
        this.downloadCount = 0;
        this.viewCount = 0;
        this.likeCount = 0;
        this.commentCount = 0;
        this.averageRating = BigDecimal.ZERO;
        this.reviewCount = 0;
        this.status = "ACTIVE";
        this.paletteData = paletteData;
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

    // 조회수/다운로드수는 동시성 안전을 위해 AssetRepository의 원자적 UPDATE로 증가시킨다.

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) this.likeCount--;
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) this.commentCount--;
    }

    // 리뷰 별점 집계 갱신 (리뷰 생성/수정/삭제 후 재계산값 반영)
    public void applyRatingStats(BigDecimal averageRating, int reviewCount) {
        this.averageRating = averageRating != null ? averageRating : BigDecimal.ZERO;
        this.reviewCount = reviewCount;
    }

    public void update(String title, String description, String thumbnailUrl,
                       BigDecimal price, boolean isFree,
                       Category category, AssetLicenseType licenseType) {
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.price = price != null ? price : BigDecimal.ZERO;
        this.isFree = isFree;
        this.category = category;
        this.licenseType = licenseType;
        this.updatedAt = LocalDateTime.now();
    }
}
