package com.expansion.server.domain.palette.dto;

import com.expansion.server.domain.palette.entity.Palette;
import com.expansion.server.domain.user.entity.Profile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** 팔레트 카드(목록) — 작성자 없음(공식)이면 author* 는 null, isOfficial=true */
@Getter
@Builder
public class PaletteSummaryResponse {

    private Long paletteId;
    private String name;
    private List<String> colors;
    private int colorCount;
    private int likeCount;
    private Long authorId;
    private String authorNickname;
    private String authorProfileImageUrl;
    @Getter(onMethod_ = @JsonProperty("isOfficial"))
    private boolean isOfficial;
    private LocalDateTime createdAt;

    public static PaletteSummaryResponse of(Palette p, List<String> colors, Profile author) {
        return PaletteSummaryResponse.builder()
                .paletteId(p.getPaletteId())
                .name(p.getName())
                .colors(colors)
                .colorCount(p.getColorCount())
                .likeCount(p.getLikeCount())
                .authorId(p.isOfficial() ? null : p.getUser().getUserId())
                .authorNickname(author != null ? author.getNickname() : null)
                .authorProfileImageUrl(author != null ? author.getProfileImageUrl() : null)
                .isOfficial(p.isOfficial())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
