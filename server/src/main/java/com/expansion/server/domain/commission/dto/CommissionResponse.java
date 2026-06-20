package com.expansion.server.domain.commission.dto;

import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.user.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    private BigDecimal agreedPrice;
    private LocalDate agreedDeadline;

    private String status;
    private String fileUrl;      // 납품 원본 — 의뢰자에겐 COMPLETED 전까지 null 마스킹(작가는 항상)
    private String previewUrl;   // 워터마크 미리보기 — 검토 단계에서 노출
    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommissionResponse of(Commission c, Profile clientProfile, Profile artistProfile,
                                        Long currentUserId) {
        // 🔒 에스크로: 원본 납품물은 작가 본인이거나 거래 완료(COMPLETED) 시에만 노출.
        //   미리보기는 검토 단계(REVIEW/COMPLETED) 또는 작가 본인에게만 — IN_PROGRESS엔 의뢰자에게 숨김.
        boolean isArtist = currentUserId != null && c.getArtist().getUserId().equals(currentUserId);
        boolean completed = "COMPLETED".equals(c.getStatus());
        boolean reviewingOrDone = "REVIEW".equals(c.getStatus()) || completed;
        String fileUrl = (isArtist || completed) ? c.getFileUrl() : null;
        String previewUrl = (isArtist || reviewingOrDone) ? c.getPreviewUrl() : null;

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
                .agreedPrice(c.getAgreedPrice())
                .agreedDeadline(c.getAgreedDeadline())
                .status(c.getStatus())
                .fileUrl(fileUrl)
                .previewUrl(previewUrl)
                .completedAt(c.getCompletedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
