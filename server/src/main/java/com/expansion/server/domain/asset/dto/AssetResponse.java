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
public class AssetResponse {

    private Long assetId;
    private String title;
    private String description;
    private String thumbnailUrl;
    private List<String> imageUrls;
    private String fileUrl;      // 다운로드 파일 (무료/구매 시에만 노출)
    private List<String> tags;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImageUrl;
    private BigDecimal price;
    // boolean 게터가 'free'로 직렬화되는 것 방지 → 게터에 @JsonProperty를 달아 'isFree' 키로 고정
    @Getter(onMethod_ = @JsonProperty("isFree"))
    private boolean isFree;
    private int downloadCount;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private BigDecimal averageRating;
    private int reviewCount;
    private Integer myRating;     // 현재 로그인 유저가 남긴 별점(없으면 null)
    private String status;
    private String licenseTypeName;
    // boolean 게터가 'liked'/'purchased'로 직렬화되는 것 방지 → 게터에 @JsonProperty로 키 고정
    @Getter(onMethod_ = @JsonProperty("isLiked"))
    private boolean isLiked;
    @Getter(onMethod_ = @JsonProperty("isPurchased"))
    private boolean isPurchased;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AssetResponse of(Asset asset, Profile profile, List<String> imageUrls,
                                   List<String> tags, boolean isLiked, boolean isPurchased,
                                   String fileUrl, Integer myRating) {
        return AssetResponse.builder()
                .assetId(asset.getAssetId())
                .title(asset.getTitle())
                .description(asset.getDescription())
                .thumbnailUrl(asset.getThumbnailUrl())
                .imageUrls(imageUrls)
                .fileUrl(fileUrl)
                .tags(tags)
                .authorId(asset.getUser().getUserId())
                .authorNickname(profile.getNickname())
                .authorProfileImageUrl(profile.getProfileImageUrl())
                .price(asset.getPrice())
                .isFree(asset.isFree())
                .downloadCount(asset.getDownloadCount())
                .viewCount(asset.getViewCount())
                .likeCount(asset.getLikeCount())
                .commentCount(asset.getCommentCount())
                .averageRating(asset.getAverageRating())
                .reviewCount(asset.getReviewCount())
                .myRating(myRating)
                .status(asset.getStatus())
                .licenseTypeName(asset.getLicenseType() != null ? asset.getLicenseType().getName() : null)
                .isLiked(isLiked)
                .isPurchased(isPurchased)
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
