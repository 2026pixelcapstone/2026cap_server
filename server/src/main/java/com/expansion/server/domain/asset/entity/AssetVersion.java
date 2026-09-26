package com.expansion.server.domain.asset.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "asset_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "version_number"})
)
@Getter
@NoArgsConstructor
public class AssetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "version_name", nullable = false, length = 50)
    private String versionName;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "change_note", columnDefinition = "TEXT")
    private String changeNote;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AssetVersion(Asset asset, int versionNumber, String versionName,
                        String fileUrl, long fileSize, String changeNote, boolean isCurrent) {
        this.asset = asset;
        this.versionNumber = versionNumber;
        this.versionName = versionName;
        this.fileUrl = fileUrl;
        this.fileSize = fileSize;
        this.changeNote = changeNote;
        this.isCurrent = isCurrent;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void markAsCurrent() {
        this.isCurrent = true;
    }

    public void unmarkCurrent() {
        this.isCurrent = false;
    }
}
