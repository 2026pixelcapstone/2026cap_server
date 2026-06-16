package com.expansion.server.domain.asset.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 에셋 평점 요약 — 상세 페이지의 평점 분포 영역용.
 * distribution = [5★, 4★, 3★, 2★, 1★] 순서의 개수.
 * count가 임계값(예: 4) 미만이면 프론트에서 "평가 부족"으로 표시.
 */
public record AssetRatingSummaryResponse(
        BigDecimal average,
        int count,
        List<Long> distribution
) {
}
