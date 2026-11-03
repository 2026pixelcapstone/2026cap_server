package com.expansion.server.domain.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
public class AssetCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    private String description;

    private String thumbnailUrl;

    private BigDecimal price = BigDecimal.ZERO;

    // Boolean이어야 Lombok 게터가 getIsFree() → JSON 키 'isFree'로 받는다.
    // (boolean이면 게터가 isFree()라 Jackson이 'free'로 보고 프론트가 보낸 'isFree'를 버림 → 무료 에셋이 유료로 저장되던 버그)
    private Boolean isFree;

    private Long categoryId;

    private Long licenseTypeId;

    private List<String> imageUrls;

    private List<String> tags;

    // 다운로드 파일 URL (R2 업로드 후 전달)
    private String fileUrl;

    // 원본 파일 이름(선택) — file_name 컬럼 length=255
    @Size(max = 255)
    private String fileName;

    // 파일 크기 (bytes)
    private long fileSize;
}
