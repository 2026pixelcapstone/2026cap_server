package com.expansion.server.domain.user.dto;

import java.math.BigDecimal;

/**
 * 내 판매 리포트(메인 왼쪽 레일 미니 보고서) — 본인만 조회.
 *
 * @param assetSales       유료 에셋 판매(환불 제외)
 * @param freeAcquisitions 내 무료 에셋을 받은 사람 수
 * @param commissions      작가로서 받은 커미션
 * @param topAsset         가장 많이 팔린 에셋(판매 없으면 null)
 */
public record SalesSummaryResponse(
        AssetSales assetSales,
        long freeAcquisitions,
        CommissionEarnings commissions,
        TopAsset topAsset) {

    /** recentDays일 이내(recentCount·recentRevenue)와 누적(count·revenue) */
    public record AssetSales(long count, BigDecimal revenue, long recentCount, BigDecimal recentRevenue, int recentDays) {}

    /** 완료 건수·받은 금액(수수료 0%)과 진행 중 건수 */
    public record CommissionEarnings(long completedCount, BigDecimal earned, long activeCount) {}

    public record TopAsset(Long assetId, String title, long sales) {}
}
