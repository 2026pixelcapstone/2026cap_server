package com.expansion.server.domain.palette.entity;

import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 공유 팔레트(V36). user == null 이면 사이트 기본 제공(공식) 팔레트 — 수정·삭제 불가.
 * colors는 '#rrggbb' JSON 배열 문자열(직렬화는 서비스 담당, 갤러리 palette_data와 같은 방식).
 */
@Entity
@Table(name = "palettes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Palette {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "palette_id")
    private Long paletteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String colors;

    @Column(name = "color_count", nullable = false)
    private int colorCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Palette(User user, String name, String description, String colors, int colorCount) {
        this.user = user;
        this.name = name;
        this.description = description;
        this.colors = colors;
        this.colorCount = colorCount;
        this.likeCount = 0;
        this.status = STATUS_ACTIVE;
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

    public boolean isOfficial() {
        return user == null;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean isOwnedBy(Long userId) {
        return user != null && userId != null && user.getUserId().equals(userId);
    }

    public void update(String name, String description, String colors, int colorCount) {
        this.name = name;
        this.description = description;
        this.colors = colors;
        this.colorCount = colorCount;
    }

    /** 삭제 — 숨김 처리(좋아요 행 등은 남아도 목록·상세에서 안 보임) */
    public void delete() {
        this.status = STATUS_DELETED;
    }
}
