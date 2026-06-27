package com.expansion.server.domain.commission.dto;

import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.user.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class CommissionResponse {

    private Long commissionId;
    private String commissionType;

    private Long clientId;
    private String clientNickname;

    private Long artistId;
    private String artistNickname;

    private Long serviceId;
    private Long requestPostId;
    private Long applicationId;
    private Long paymentId;

    // 거래 기록 스냅샷 — 수락 시점의 의뢰글/작가서비스 제목·내용(원글 삭제돼도 보존)
    private String title;
    private String description;

    private BigDecimal agreedPrice;
    private LocalDate agreedDeadline;

    private String status;
    private List<DeliveryFileDto> deliveryFiles;   // 납품 원본(다중) — 의뢰자에겐 COMPLETED 전까지 빈 리스트(작가는 항상)
    private List<PreviewImageDto> previewImages;   // 워터마크 미리보기(다중) — 검토 단계에서 노출

    // 타임라인 — 단계 전이 시각. (수락=createdAt, 검토요청=reviewRequestedAt, 완료=completedAt, 취소=cancelledAt)
    private LocalDateTime reviewRequestedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class PreviewImageDto {
        private Long previewImageId;   // 작가의 삭제용
        private String imageUrl;
    }

    @Getter
    @Builder
    public static class DeliveryFileDto {
        private Long fileId;       // 작가의 삭제용
        private String fileUrl;
        private String fileName;
    }

    public static CommissionResponse of(Commission c, Profile clientProfile, Profile artistProfile,
                                        Long currentUserId) {
        // 🔒 에스크로: 원본 납품물은 작가 본인이거나 거래 완료(COMPLETED) 시에만 노출.
        //   미리보기는 검토 단계(REVIEW/COMPLETED) 또는 작가 본인에게만 — IN_PROGRESS엔 의뢰자에게 숨김(빈 리스트).
        boolean isArtist = currentUserId != null && c.getArtist().getUserId().equals(currentUserId);
        boolean completed = "COMPLETED".equals(c.getStatus());
        boolean reviewingOrDone = "REVIEW".equals(c.getStatus()) || completed;
        Long artistUserId = c.getArtist().getUserId();

        // 납품 원본(작가 업로드 파일만) — 작가 본인이거나 완료(COMPLETED) 시에만 노출, 그 외 빈 리스트.
        List<DeliveryFileDto> deliveryFiles = (isArtist || completed)
                ? c.getFiles().stream()
                    .filter(f -> f.getUploader().getUserId().equals(artistUserId))
                    .map(f -> DeliveryFileDto.builder()
                            .fileId(f.getFileId())
                            .fileUrl(f.getFileUrl())
                            .fileName(f.getFileName())
                            .build())
                    .toList()
                : Collections.emptyList();

        List<PreviewImageDto> previewImages = (isArtist || reviewingOrDone)
                ? c.getPreviewImages().stream()
                    .map(p -> PreviewImageDto.builder()
                            .previewImageId(p.getPreviewImageId())
                            .imageUrl(p.getImageUrl())
                            .build())
                    .toList()
                : Collections.emptyList();

        return CommissionResponse.builder()
                .commissionId(c.getCommissionId())
                .commissionType(c.getCommissionType())
                .clientId(c.getClient().getUserId())
                .clientNickname(clientProfile != null ? clientProfile.getNickname() : null)
                .artistId(c.getArtist().getUserId())
                .artistNickname(artistProfile != null ? artistProfile.getNickname() : null)
                .serviceId(c.getServiceId())
                .requestPostId(c.getRequestPostId())
                .applicationId(c.getApplicationId())
                .paymentId(c.getPaymentId())
                .title(c.getTitle())
                .description(c.getDescription())
                .agreedPrice(c.getAgreedPrice())
                .agreedDeadline(c.getAgreedDeadline())
                .status(c.getStatus())
                .deliveryFiles(deliveryFiles)
                .previewImages(previewImages)
                .reviewRequestedAt(c.getReviewRequestedAt())
                .cancelledAt(c.getCancelledAt())
                .completedAt(c.getCompletedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
