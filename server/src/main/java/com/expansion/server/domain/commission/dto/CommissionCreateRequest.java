package com.expansion.server.domain.commission.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 작가 서비스 의뢰(Commission 생성) 요청 — 서비스 상세의 '의뢰하기' 전용.
 * 작가·금액은 서버가 서비스 기준으로 정한다(artistId는 일치 확인, 정액은 basePrice, 견적은 범위 검사).
 * 의뢰글 계약(REQUEST)은 이 요청으로 만들 수 없고 지원 수락으로만 생긴다.
 */
@Getter
@NoArgsConstructor
public class CommissionCreateRequest {

    @NotNull
    private String commissionType;  // SERVICE_OPTION / SERVICE_QUOTE

    @NotNull
    private Long artistId;

    private Long serviceId;
    private Long requestPostId;
    private Long applicationId;

    @NotNull
    private BigDecimal agreedPrice;

    private LocalDate agreedDeadline;
}
