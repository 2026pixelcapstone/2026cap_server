package com.expansion.server.domain.user.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "social_id")
    private String socialId;

    @Column(name = "social_provider", length = 20)
    private String socialProvider;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(String email, String passwordHash, String role, String status,
                boolean emailVerified, String socialId, String socialProvider) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : "USER";
        this.status = status != null ? status : "ACTIVE";
        this.emailVerified = emailVerified;
        this.socialId = socialId;
        this.socialProvider = socialProvider;
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

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void verifyEmail() {
        this.emailVerified = true;
        this.updatedAt = LocalDateTime.now();
    }

    // 소셜 계정 연결 — 기존(이메일 가입) 유저가 동일 이메일로 소셜 로그인 시 1회 연결.
    // 소셜 제공자가 이메일을 검증하므로 email_verified도 함께 true.
    public void linkSocial(String provider, String socialId) {
        if (this.socialProvider == null) {
            this.socialProvider = provider;
            this.socialId = socialId;
        }
        this.emailVerified = true;
        this.updatedAt = LocalDateTime.now();
    }

    /** 비밀번호 로그인이 가능한 계정인지 — 소셜 전용 계정은 password_hash가 NULL */
    public boolean hasPassword() {
        return this.passwordHash != null;
    }

    /** 비밀번호 변경·재설정 — 이미 인코딩된 해시를 받는다 */
    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 회원 탈퇴 — 행은 남기고(거래·결제·댓글 기록이 이 행을 참조) 개인정보를 지운다.
     * 이메일은 바로 재가입할 수 있게 비워 두는 대신 고유한 가짜 주소로(email은 NOT NULL·UNIQUE, .invalid는 실제로 쓰일 수 없는 도메인).
     */
    public void withdraw() {
        this.status = "DELETED";
        this.deletedAt = LocalDateTime.now();
        this.email = "deleted_" + this.userId + "@withdrawn.invalid";
        this.passwordHash = null;
        this.socialId = null;
        this.socialProvider = null;
        this.emailVerified = false;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }
}
