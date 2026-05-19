package com.expansion.server.domain.common.dto;

import com.expansion.server.domain.common.entity.Tag;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TagResponse {

    private Long tagId;
    private String tagName;
    private int postCount;

    public static TagResponse from(Tag tag) {
        return TagResponse.builder()
                .tagId(tag.getTagId())
                .tagName(tag.getTagName())
                .postCount(tag.getPostCount())
                .build();
    }
}
