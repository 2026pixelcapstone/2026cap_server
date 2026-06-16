package com.expansion.server.domain.asset.dto;

import com.expansion.server.domain.asset.entity.AssetComment;
import com.expansion.server.domain.user.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AssetCommentResponse {

    private Long commentId;
    private Long assetId;
    private Long parentId;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImageUrl;
    private String content;
    private Integer rating;        // 리뷰 별점(1~5), 일반 댓글이면 null
    private boolean isDeleted;
    private int replyCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AssetCommentResponse of(AssetComment comment, Profile profile) {
        return AssetCommentResponse.builder()
                .commentId(comment.getCommentId())
                .assetId(comment.getAsset().getAssetId())
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null)
                .authorId(comment.getUser().getUserId())
                .authorNickname(profile.getNickname())
                .authorProfileImageUrl(profile.getProfileImageUrl())
                .content(comment.isDeleted() ? null : comment.getContent())
                .rating(comment.isDeleted() ? null : comment.getRating())
                .isDeleted(comment.isDeleted())
                .replyCount(comment.getReplies().size())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
