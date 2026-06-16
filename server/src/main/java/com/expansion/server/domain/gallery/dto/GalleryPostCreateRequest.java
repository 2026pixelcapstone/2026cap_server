package com.expansion.server.domain.gallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GalleryPostCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 2000)
    private String description;

    private String thumbnailUrl;

    // "FREE" | "DEDICATED"
    @NotBlank
    private String galleryType;

    // "PUBLIC" | "PRIVATE" | "UNLISTED"
    private String visibility;

    private Long categoryId;

    private Long projectId;   // 전용 갤러리 공유 시 에디터 프로젝트 연결

    private boolean isEditable;

    private boolean isCollaborative;

    private Long originPostId;   // 리믹스 시 원본 포스트 ID

    private List<String> imageUrls;  // 이미지 URL 목록 (순서 = sortOrder)

    private List<String> tags;       // 태그 이름 목록

    // ── 전용 갤러리(.ppit) 전용 (FREE는 미사용) ──
    private String fileUrl;          // .ppit 원본 R2 URL

    private Integer canvasWidth;

    private Integer canvasHeight;

    private PaletteData palette;     // 팔레트 {name?, colors[]} → palette_data(JSONB)

    private DedicatedVisibility dedicatedVisibility;  // 공개 토글 → dedicated_visibility(JSONB)
}
