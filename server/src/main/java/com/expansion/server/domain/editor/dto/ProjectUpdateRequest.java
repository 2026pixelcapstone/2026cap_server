package com.expansion.server.domain.editor.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectUpdateRequest {

    @Size(max = 100)
    private String title;

    private int width;

    private int height;

    private String thumbnailUrl;

    private Boolean isPublic;
}
