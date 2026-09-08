package com.expansion.server.domain.editor.entity;

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
@Table(name = "projects")
@Getter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "background_color", length = 10)
    private String backgroundColor;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "ai_analyzed", nullable = false)
    private boolean aiAnalyzed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "palette_data", columnDefinition = "jsonb")
    private String paletteData;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("frameOrder ASC")
    private List<Frame> frames = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectMember> projectMembers = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Project(User user, String title, int width, int height, String backgroundColor,
                   String fileUrl, String thumbnailUrl, boolean isPublic,
                   String status, boolean aiAnalyzed, String paletteData) {
        this.user = user;
        this.title = title;
        this.width = width > 0 ? width : 32;
        this.height = height > 0 ? height : 32;
        this.backgroundColor = backgroundColor;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.isPublic = isPublic;
        this.status = status != null ? status : "ACTIVE";
        this.aiAnalyzed = aiAnalyzed;
        this.paletteData = paletteData;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String title, String thumbnailUrl, boolean isPublic) {
        if (title != null) this.title = title;
        if (thumbnailUrl != null) this.thumbnailUrl = thumbnailUrl;
        this.isPublic = isPublic;
    }

    public void updateFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
        this.lastAccessedAt = LocalDateTime.now();
    }

    public void markAiAnalyzed() {
        this.aiAnalyzed = true;
    }

    public void softDelete() {
        this.status = "DELETED";
    }
}
