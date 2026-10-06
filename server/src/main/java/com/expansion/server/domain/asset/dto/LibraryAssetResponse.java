package com.expansion.server.domain.asset.dto;

import com.expansion.server.domain.asset.entity.Asset;
import com.expansion.server.domain.asset.entity.AssetPurchase;
import com.expansion.server.domain.user.entity.Profile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 마이페이지 '구매/받은 에셋' 카드 — 에셋 요약 + 획득 정보(언제·얼마에). */
@Getter
@Builder
public class LibraryAssetResponse {

    private Long purchaseId;
    private Long assetId;
    private String title;
    private String thumbnailUrl;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImageUrl;
    /** 에셋의 현재 가격(참고용) */
    private BigDecimal price;
    @Getter(onMethod_ = @JsonProperty("isFree"))
    private boolean isFree;
    /** ACTIVE / DELETED(판매 중지 — 소유자는 계속 다운로드 가능) */
    private String assetStatus;
    /** 구매일 또는 무료로 받은 날 */
    private LocalDateTime acquiredAt;
    /** 실제 결제 금액. 무료 취득이면 null */
    private BigDecimal pricePaid;

    public static LibraryAssetResponse of(AssetPurchase purchase, Profile authorProfile) {
        Asset asset = purchase.getAsset();
        return LibraryAssetResponse.builder()
                .purchaseId(purchase.getPurchaseId())
                .assetId(asset.getAssetId())
                .title(asset.getTitle())
                .thumbnailUrl(asset.getThumbnailUrl())
                .authorId(asset.getUser().getUserId())
                .authorNickname(authorProfile != null ? authorProfile.getNickname() : null)
                .authorProfileImageUrl(authorProfile != null ? authorProfile.getProfileImageUrl() : null)
                .price(asset.getPrice())
                .isFree(asset.isFree())
                .assetStatus(asset.getStatus())
                .acquiredAt(purchase.getCreatedAt())
                .pricePaid(purchase.isFreeAcquisition() ? null : purchase.getPricePaid())
                .build();
    }
}
