package com.expansion.server.domain.commission.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 커미션 검토용 미리보기 이미지(워터마크+축소). 작가가 여러 장 업로드, 의뢰자는 검토 단계에서 열람.
 * commission_service_images / request_post_images 와 동일 패턴.
 */
@Entity
@Table(name = "commission_preview_images")
@Getter
@NoArgsConstructor
public class CommissionPreviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preview_image_id")
    private Long previewImageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_id", nullable = false)
    private Commission commission;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CommissionPreviewImage(Commission commission, String imageUrl, int sortOrder) {
        this.commission = commission;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
