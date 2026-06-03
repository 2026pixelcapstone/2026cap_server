package com.expansion.server.domain.commission.dto;

import com.expansion.server.domain.commission.entity.CommissionApplication;
import com.expansion.server.domain.user.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CommissionApplicationResponse {

    private Long applicationId;
    private Long requestPostId;
    private String requestPostTitle;
    private Long artistId;
    private String artistNickname;
    private String artistProfileImageUrl;
    private String message;
    private BigDecimal proposedPrice;
    private String status;
    private LocalDateTime createdAt;

    // 수락 시 생성된 커미션 정보 (ACCEPTED 지원에만 존재) — 거래룸 바로가기/취소 상태 표시용
    private Long commissionId;
    private String commissionStatus; // IN_PROGRESS / REVIEW / COMPLETED / CANCELLED

    public static CommissionApplicationResponse of(CommissionApplication app, Profile artistProfile) {
        return of(app, artistProfile, null, null);
    }

    public static CommissionApplicationResponse of(CommissionApplication app, Profile artistProfile,
                                                   Long commissionId, String commissionStatus) {
        return CommissionApplicationResponse.builder()
                .applicationId(app.getApplicationId())
                .requestPostId(app.getRequestPost().getRequestPostId())
                .requestPostTitle(app.getRequestPost().getTitle())
                .artistId(app.getArtist().getUserId())
                .artistNickname(artistProfile != null ? artistProfile.getNickname() : null)
                .artistProfileImageUrl(artistProfile != null ? artistProfile.getProfileImageUrl() : null)
                .message(app.getMessage())
                .proposedPrice(app.getProposedPrice())
                .status(app.getStatus())
                .createdAt(app.getCreatedAt())
                .commissionId(commissionId)
                .commissionStatus(commissionStatus)
                .build();
    }
}
