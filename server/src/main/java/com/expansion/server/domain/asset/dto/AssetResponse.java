package com.expansion.server.domain.asset.dto;

import com.expansion.server.domain.asset.entity.Asset;
import com.expansion.server.domain.user.entity.Profile;
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
    private boolean isFree;
    private int downloadCount;
    private int likeCount;
    private int commentCount;
    private String status;
    private String licenseTypeName;
    private boolean isLiked;
    private boolean isPurchased;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AssetResponse of(Asset asset, Profile profile, List<String> imageUrls,
                                   List<String> tags, boolean isLiked, boolean isPurchased,
                                   String fileUrl) {
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
                .likeCount(asset.getLikeCount())
                .commentCount(asset.getCommentCount())
                .status(asset.getStatus())
                .licenseTypeName(asset.getLicenseType() != null ? asset.getLicenseType().getName() : null)
                .isLiked(isLiked)
                .isPurchased(isPurchased)
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
