package com.expansion.server.domain.gallery.entity;

import com.expansion.server.domain.common.entity.Category;
import com.expansion.server.domain.editor.entity.Project;
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
@Table(name = "gallery_posts")
@Getter
@NoArgsConstructor
public class GalleryPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_post_id")
    private GalleryPost originPost;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "gallery_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private GalleryType galleryType;   // FREE, DEDICATED

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Visibility visibility;     // PUBLIC, PRIVATE, UNLISTED

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "is_editable", nullable = false)
    private boolean isEditable;

    @Column(name = "remix_count", nullable = false)
    private int remixCount;

    @Column(name = "is_collaborative", nullable = false)
    private boolean isCollaborative;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "palette_data", columnDefinition = "jsonb")
    private String paletteData;

    // ── 전용 갤러리(.ppit) 전용 필드 (FREE 게시글은 전부 null) ──
    @Column(name = "file_url", length = 500)
    private String fileUrl;             // .ppit 원본 R2 URL

    @Column(name = "canvas_width")
    private Integer canvasWidth;

    @Column(name = "canvas_height")
    private Integer canvasHeight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dedicated_visibility", columnDefinition = "jsonb")
    private String dedicatedVisibility; // 공개 토글 {canvas,palette,layers,download} JSON

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<GalleryImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostTag> postTags = new ArrayList<>();

    @Builder
    public GalleryPost(User user, Project project, Category category, String title, String description,
                       String thumbnailUrl, GalleryType galleryType, Visibility visibility,
                       boolean isEditable, GalleryPost originPost, boolean isCollaborative,
                       String paletteData, String fileUrl, Integer canvasWidth, Integer canvasHeight,
                       String dedicatedVisibility) {
        this.user = user;
        this.project = project;
        this.category = category;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.galleryType = galleryType != null ? galleryType : GalleryType.FREE;
        this.visibility = visibility != null ? visibility : Visibility.PUBLIC;
        this.isEditable = isEditable;
        this.originPost = originPost;
        this.isCollaborative = isCollaborative;
        this.paletteData = paletteData;
        this.fileUrl = fileUrl;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.dedicatedVisibility = dedicatedVisibility;
        this.viewCount = 0;
        this.likeCount = 0;
        this.commentCount = 0;
        this.remixCount = 0;
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

    public void update(String title, String description, String thumbnailUrl,
                       Visibility visibility, boolean isEditable, Category category) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (thumbnailUrl != null) this.thumbnailUrl = thumbnailUrl;
        if (visibility != null) this.visibility = visibility;
        this.isEditable = isEditable;
        if (category != null) this.category = category;
    }

    /** 전용 갤러리(.ppit) 메타/팔레트/공개토글 갱신 — null 인자는 기존값 유지 */
    public void updateDedicated(String paletteData, String fileUrl,
                                Integer canvasWidth, Integer canvasHeight,
                                String dedicatedVisibility) {
        if (paletteData != null) this.paletteData = paletteData;
        if (fileUrl != null) this.fileUrl = fileUrl;
        if (canvasWidth != null) this.canvasWidth = canvasWidth;
        if (canvasHeight != null) this.canvasHeight = canvasHeight;
        if (dedicatedVisibility != null) this.dedicatedVisibility = dedicatedVisibility;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void incrementViewCount() { this.viewCount++; }
    public void incrementLikeCount() { this.likeCount++; }
    public void decrementLikeCount() { if (this.likeCount > 0) this.likeCount--; }
    public void incrementCommentCount() { this.commentCount++; }
    public void decrementCommentCount() { if (this.commentCount > 0) this.commentCount--; }
    public void incrementRemixCount() { this.remixCount++; }

    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }
}
