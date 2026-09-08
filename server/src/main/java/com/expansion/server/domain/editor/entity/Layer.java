package com.expansion.server.domain.editor.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "layers")
@Getter
@NoArgsConstructor
public class Layer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "layer_id")
    private Long layerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frame_id", nullable = false)
    private Frame frame;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "layer_order", nullable = false)
    private int layerOrder;

    @Column(name = "blend_mode", nullable = false, length = 20)
    private String blendMode;

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked;

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible;

    @Column(nullable = false)
    private float opacity;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Layer(Frame frame, String name, int layerOrder, String blendMode,
                 boolean isLocked, boolean isVisible, float opacity, String fileUrl) {
        this.frame = frame;
        this.name = name;
        this.layerOrder = layerOrder;
        this.blendMode = blendMode != null ? blendMode : "NORMAL";
        this.isLocked = isLocked;
        this.isVisible = isVisible;
        this.opacity = opacity > 0 ? opacity : 1.0f;
        this.fileUrl = fileUrl;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.blendMode == null) this.blendMode = "NORMAL";
        if (this.opacity == 0) this.opacity = 1.0f;
        this.isVisible = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String name, float opacity, boolean isVisible, boolean isLocked, String fileUrl, String pixelData) {
        if (name != null) this.name = name;
        this.opacity = opacity;
        this.isVisible = isVisible;
        this.isLocked = isLocked;
        if (fileUrl != null) this.fileUrl = fileUrl;
    }

    public void reorder(int newOrder) {
        this.layerOrder = newOrder;
    }
}
