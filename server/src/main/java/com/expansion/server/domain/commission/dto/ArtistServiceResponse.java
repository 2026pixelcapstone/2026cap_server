package com.expansion.server.domain.commission.dto;

import com.expansion.server.domain.commission.entity.ArtistService;
import com.expansion.server.domain.user.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ArtistServiceResponse {

    private Long serviceId;
    private Long artistId;
    private String artistNickname;
    private String artistProfileImageUrl;
    private String title;
    private String description;
    private String serviceType;
    private BigDecimal basePrice;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private Integer estimatedDays;
    private String category;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ArtistServiceResponse of(ArtistService service, Profile profile) {
        return ArtistServiceResponse.builder()
                .serviceId(service.getServiceId())
                .artistId(service.getArtist().getUserId())
                .artistNickname(profile != null ? profile.getNickname() : null)
                .artistProfileImageUrl(profile != null ? profile.getProfileImageUrl() : null)
                .title(service.getTitle())
                .description(service.getDescription())
                .serviceType(service.getServiceType())
                .basePrice(service.getBasePrice())
                .priceMin(service.getPriceMin())
                .priceMax(service.getPriceMax())
                .estimatedDays(service.getEstimatedDays())
                .category(service.getCategory())
                .status(service.getStatus())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
