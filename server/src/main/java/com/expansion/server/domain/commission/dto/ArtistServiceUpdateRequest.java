package com.expansion.server.domain.commission.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ArtistServiceUpdateRequest {

    private String title;
    private String description;
    private String serviceType;
    private BigDecimal basePrice;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private Integer estimatedDays;
    private String category;
}
