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
public class CommissionSummary {

    private Long commissionId;
    private String commissionType;

    private Long clientId;
    private String clientNickname;

    private Long artistId;
    private String artistNickname;

    private BigDecimal agreedPrice;
    private LocalDate agreedDeadline;
    private String status;

    private LocalDateTime createdAt;

    private long unreadCount; // 내가 안 읽은 채팅 메시지 수 (목록 배지용)

    public static CommissionSummary of(Commission c, Profile clientProfile, Profile artistProfile) {
        return of(c, clientProfile, artistProfile, 0L);
    }

    public static CommissionSummary of(Commission c, Profile clientProfile, Profile artistProfile, long unreadCount) {
        return CommissionSummary.builder()
                .commissionId(c.getCommissionId())
                .commissionType(c.getCommissionType())
                .clientId(c.getClient().getUserId())
                .clientNickname(clientProfile != null ? clientProfile.getNickname() : null)
                .artistId(c.getArtist().getUserId())
                .artistNickname(artistProfile != null ? artistProfile.getNickname() : null)
                .agreedPrice(c.getAgreedPrice())
                .agreedDeadline(c.getAgreedDeadline())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .unreadCount(unreadCount)
                .build();
    }
}
