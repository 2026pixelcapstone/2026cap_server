package com.expansion.server.domain.asset.dto;

import com.expansion.server.domain.asset.entity.Asset;
import com.expansion.server.domain.user.entity.Profile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AssetSummary {

    private Long assetId;
    private String title;
    private String thumbnailUrl;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImageUrl;
    private BigDecimal price;
    @Getter(onMethod_ = @JsonProperty("isFree"))
    private boolean isFree;
    private int downloadCount;
    private int likeCount;
    private int commentCount;
    private BigDecimal averageRating;
    private int reviewCount;
    private String categoryName;
    private String status;
    private LocalDateTime createdAt;
    private List<String> tags;

    public static AssetSummary of(Asset asset, Profile profile, List<String> tags) {
        return AssetSummary.builder()
                .assetId(asset.getAssetId())
                .title(asset.getTitle())
                .thumbnailUrl(asset.getThumbnailUrl())
                .authorId(asset.getUser().getUserId())
                .authorNickname(profile.getNickname())
                .authorProfileImageUrl(profile.getProfileImageUrl())
                .price(asset.getPrice())
                .isFree(asset.isFree())
                .downloadCount(asset.getDownloadCount())
                .likeCount(asset.getLikeCount())
                .commentCount(asset.getCommentCount())
                .averageRating(asset.getAverageRating())
                .reviewCount(asset.getReviewCount())
                .categoryName(asset.getCategory() != null ? asset.getCategory().getName() : null)
                .status(asset.getStatus())
                .createdAt(asset.getCreatedAt())
                .tags(tags != null ? tags : List.of())
                .build();
    }
}
