package com.expansion.server.domain.commission.entity;

import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commissions")
@Getter
@NoArgsConstructor
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "commission_id")
    private Long commissionId;

    @Column(name = "commission_type", nullable = false, length = 20)
    private String commissionType;
    // SERVICE_OPTION / SERVICE_QUOTE / REQUEST

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "request_post_id")
    private Long requestPostId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "agreed_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal agreedPrice;

    @Column(name = "agreed_deadline")
    private LocalDate agreedDeadline;

    @Column(nullable = false, length = 20)
    private String status;
    // IN_PROGRESS / REVIEW / COMPLETED / CANCELLED

    @Column(name = "file_url", length = 500)
    private String fileUrl;   // 납품 원본 — 완료 전까지 의뢰자에게 마스킹

    @Column(name = "preview_url", length = 500)
    private String previewUrl;   // 워터마크+축소 미리보기 — 검토 단계에서 의뢰자에게 노출

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "commission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommissionFile> files = new ArrayList<>();

    @OneToMany(mappedBy = "commission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CommissionPreviewImage> previewImages = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Commission(String commissionType, User client, User artist,
                      Long serviceId, Long requestPostId, Long applicationId,
                      BigDecimal agreedPrice, LocalDate agreedDeadline, String status) {
        this.commissionType = commissionType;
        this.client = client;
        this.artist = artist;
        this.serviceId = serviceId;
        this.requestPostId = requestPostId;
        this.applicationId = applicationId;
        this.agreedPrice = agreedPrice;
        this.agreedDeadline = agreedDeadline;
        this.status = status != null ? status : "IN_PROGRESS";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "IN_PROGRESS";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(String newStatus) {
        this.status = newStatus;
        if ("COMPLETED".equals(newStatus)) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public void cancel() {
        this.status = "CANCELLED";
    }

    // ─── 미리보기 이미지 (다중) ───────────────────────────────────────────────
    public CommissionPreviewImage addPreviewImage(String imageUrl) {
        int nextOrder = previewImages.stream()
                .mapToInt(CommissionPreviewImage::getSortOrder)
                .max().orElse(-1) + 1;
        CommissionPreviewImage img = CommissionPreviewImage.builder()
                .commission(this)
                .imageUrl(imageUrl)
                .sortOrder(nextOrder)
                .build();
        previewImages.add(img);
        return img;
    }

    public void removePreviewImage(CommissionPreviewImage img) {
        previewImages.remove(img);   // orphanRemoval → DB 삭제
    }
}
