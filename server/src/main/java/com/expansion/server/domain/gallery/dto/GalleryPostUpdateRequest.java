package com.expansion.server.domain.gallery.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GalleryPostUpdateRequest {

    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    private String thumbnailUrl;

    // "PUBLIC" | "PRIVATE" | "UNLISTED"
    private String visibility;

    private Long categoryId;

    private Boolean isEditable;

    private List<String> imageUrls;

    private List<String> tags;

    // ── 전용 갤러리(.ppit) 편집 (null = 기존값 유지) ──
    private String fileUrl;

    private Integer canvasWidth;

    private Integer canvasHeight;

    private PaletteData palette;

    private DedicatedVisibility dedicatedVisibility;
}
