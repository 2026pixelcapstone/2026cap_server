package com.expansion.server.domain.user.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Long profileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(length = 255)
    private String bio;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "follower_count", nullable = false)
    private int followerCount;

    @Column(name = "following_count", nullable = false)
    private int followingCount;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Profile(User user, String nickname, String bio, String websiteUrl,
                   String profileImageUrl, boolean isPublic) {
        this.user = user;
        this.nickname = nickname;
        this.bio = bio;
        this.websiteUrl = websiteUrl;
        this.profileImageUrl = profileImageUrl;
        this.followerCount = 0;
        this.followingCount = 0;
        this.isPublic = isPublic;
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

    /** 프로필 텍스트 정보 수정 — 프로필 이미지는 changeProfileImage로만 바꿈(PATCH가 사진을 지우지 않도록) */
    public void update(String nickname, String bio, String websiteUrl, boolean isPublic) {
        this.nickname = nickname;
        this.bio = bio;
        this.websiteUrl = websiteUrl;
        this.isPublic = isPublic;
        this.updatedAt = LocalDateTime.now();
    }

    /** 프로필 이미지 교체/제거(null) — 서버가 업로드한 URL만 들어옴 */
    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseFollowerCount() {
        this.followerCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void decreaseFollowerCount() {
        if (this.followerCount > 0) this.followerCount--;
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseFollowingCount() {
        this.followingCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void decreaseFollowingCount() {
        if (this.followingCount > 0) this.followingCount--;
        this.updatedAt = LocalDateTime.now();
    }
}
