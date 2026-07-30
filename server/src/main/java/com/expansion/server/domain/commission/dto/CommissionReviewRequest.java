package com.expansion.server.domain.commission.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커미션 리뷰 작성/수정 요청 — 별점 필수(1~5), 내용 선택.
 */
@Getter
@NoArgsConstructor
public class CommissionReviewRequest {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 2000)
    private String content;
}
