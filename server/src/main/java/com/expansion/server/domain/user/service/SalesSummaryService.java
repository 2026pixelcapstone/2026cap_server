package com.expansion.server.domain.user.service;

import com.expansion.server.domain.asset.entity.AssetPurchase;
import com.expansion.server.domain.asset.repository.AssetPurchaseRepository;
import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.domain.commission.service.CommissionService;
import com.expansion.server.domain.user.dto.SalesSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 내 판매 리포트 — 에셋 판매·무료 취득·커미션 수익을 집계 쿼리로만 계산(행을 불러오지 않음).
 * 저장하지 않고 요청 때마다 계산한다(규모가 작아 충분, 커지면 asset_sales_stats 등 집계 테이블 검토).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesSummaryService {

    static final int RECENT_DAYS = 30;

    private final AssetPurchaseRepository assetPurchaseRepository;
    private final CommissionRepository commissionRepository;

    public SalesSummaryResponse getMySummary(Long userId) {
        AssetPurchaseRepository.SalesRow sales = assetPurchaseRepository.summarizePaidSales(
                userId, LocalDateTime.now().minusDays(RECENT_DAYS));
        long freeAcquisitions = assetPurchaseRepository.countByAsset_User_UserIdAndStatusAndPaymentIdIsNull(
                userId, AssetPurchase.STATUS_ACTIVE);
        CommissionRepository.EarningsRow earnings = commissionRepository.summarizeCompletedAsArtist(userId);
        long activeCommissions = commissionRepository.countByArtist_UserIdAndStatusIn(
                userId, CommissionService.ACTIVE_STATUSES);
        SalesSummaryResponse.TopAsset top = assetPurchaseRepository.findTopSoldAssets(userId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(r -> new SalesSummaryResponse.TopAsset(r.getAssetId(), r.getTitle(), r.getSales()))
                .orElse(null);

        return new SalesSummaryResponse(
                new SalesSummaryResponse.AssetSales(
                        toLong(sales.getTotalCount()), toMoney(sales.getTotalRevenue()),
                        toLong(sales.getRecentCount()), toMoney(sales.getRecentRevenue()), RECENT_DAYS),
                freeAcquisitions,
                new SalesSummaryResponse.CommissionEarnings(
                        toLong(earnings.getCount()), toMoney(earnings.getAmount()), activeCommissions),
                top);
    }

    // 집계 결과 숫자 타입은 DB·Hibernate에 따라 Integer/Long/BigDecimal로 올 수 있어 Number로 받아 변환
    private static long toLong(Number n) {
        return n == null ? 0L : n.longValue();
    }

    // 원(KRW) 단위 정수로(35000.00 → 35000). stripTrailingZeros는 3.5E+4처럼 지수 표기가 될 수 있어 쓰지 않음
    private static BigDecimal toMoney(Number n) {
        return n == null ? BigDecimal.ZERO : new BigDecimal(n.toString()).setScale(0, RoundingMode.HALF_UP);
    }
}
