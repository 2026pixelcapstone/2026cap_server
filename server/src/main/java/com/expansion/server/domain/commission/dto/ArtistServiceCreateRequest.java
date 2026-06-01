package com.expansion.server.domain.commission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ArtistServiceCreateRequest {

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String serviceType;  // OPTION / QUOTE

    private BigDecimal basePrice;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private Integer estimatedDays;

    private String category;  // 캐릭터 / 배경·환경 / 애니메이션 / 게임 에셋 / 초상화 / 기타
}
