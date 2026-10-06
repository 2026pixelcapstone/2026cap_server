package com.expansion.server.domain.palette.dto;

import com.expansion.server.domain.palette.entity.Palette;
import com.expansion.server.domain.user.entity.Profile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** 팔레트 상세 — 목록 필드 + 설명·내 좋아요 여부·수정 가능 여부 */
@Getter
@Builder
public class PaletteResponse {

    private Long paletteId;
    private String name;
    private String description;
    private List<String> colors;
    private int colorCount;
    private int likeCount;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImageUrl;
    @Getter(onMethod_ = @JsonProperty("isOfficial"))
    private boolean isOfficial;
    @Getter(onMethod_ = @JsonProperty("isLiked"))
    private boolean isLiked;
    /** 현재 사용자가 수정·삭제할 수 있는지(작성자 본인, 공식 팔레트는 항상 false) */
    @Getter(onMethod_ = @JsonProperty("isMine"))
    private boolean isMine;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaletteResponse of(Palette p, List<String> colors, Profile author, boolean liked, boolean mine) {
        return PaletteResponse.builder()
                .paletteId(p.getPaletteId())
                .name(p.getName())
                .description(p.getDescription())
                .colors(colors)
                .colorCount(p.getColorCount())
                .likeCount(p.getLikeCount())
                .authorId(p.isOfficial() ? null : p.getUser().getUserId())
                .authorNickname(author != null ? author.getNickname() : null)
                .authorProfileImageUrl(author != null ? author.getProfileImageUrl() : null)
                .isOfficial(p.isOfficial())
                .isLiked(liked)
                .isMine(mine)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
