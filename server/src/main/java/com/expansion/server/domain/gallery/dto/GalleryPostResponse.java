package com.expansion.server.domain.gallery.dto;

import com.expansion.server.domain.gallery.entity.GalleryPost;
import com.expansion.server.domain.user.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class GalleryPostResponse {

    private Long postId;
    private String title;
    private String description;
    private String thumbnailUrl;
    private List<String> imageUrls;
    private List<String> tags;

    private Long projectId;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImageUrl;

    private int viewCount;
    private int likeCount;
    private int commentCount;
    private int remixCount;

    private String galleryType;
    private String visibility;

    private boolean isEditable;
    private boolean isCollaborative;
    private Long originPostId;

    private boolean isLiked;

    // ── 전용 갤러리(.ppit) — FREE 게시글은 전부 null ──
    private String fileUrl;          // download=false & 비소유자면 서비스에서 null 마스킹
    private Integer canvasWidth;
    private Integer canvasHeight;
    private PaletteData palette;
    private DedicatedVisibility dedicatedVisibility;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GalleryPostResponse of(GalleryPost post, Profile profile,
                                         List<String> imageUrls, List<String> tags,
                                         boolean isLiked, String fileUrl,
                                         PaletteData palette, DedicatedVisibility dedicatedVisibility) {
        return GalleryPostResponse.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .description(post.getDescription())
                .thumbnailUrl(post.getThumbnailUrl())
                .imageUrls(imageUrls)
                .tags(tags)
                .projectId(post.getProject() != null ? post.getProject().getProjectId() : null)
                .authorId(post.getUser().getUserId())
                .authorNickname(profile != null ? profile.getNickname() : null)
                .authorProfileImageUrl(profile != null ? profile.getProfileImageUrl() : null)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .remixCount(post.getRemixCount())
                .galleryType(post.getGalleryType().name())
                .visibility(post.getVisibility().name())
                .isEditable(post.isEditable())
                .isCollaborative(post.isCollaborative())
                .originPostId(post.getOriginPost() != null ? post.getOriginPost().getPostId() : null)
                .isLiked(isLiked)
                .fileUrl(fileUrl)
                .canvasWidth(post.getCanvasWidth())
                .canvasHeight(post.getCanvasHeight())
                .palette(palette)
                .dedicatedVisibility(dedicatedVisibility)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
