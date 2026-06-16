package com.expansion.server.domain.asset.entity;

import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 다운로드 기록 — 사람×에셋 1행(UNIQUE). 다운로드 수를 "받은 사람 수(중복 제거)"로 집계하기 위함.
 * 같은 사용자가 다시 받아도 행이 1개라 카운트가 중복 증가하지 않는다.
 */
@Entity
@Table(
        name = "asset_downloads",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "asset_id"})
)
@Getter
@NoArgsConstructor
public class AssetDownload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "download_id")
    private Long downloadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AssetDownload(User user, Asset asset) {
        this.user = user;
        this.asset = asset;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
