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

    private boolean isFree;

    private Long categoryId;

    private Long licenseTypeId;

    private List<String> imageUrls;

    private List<String> tags;

    // 다운로드 파일 URL (R2 업로드 후 전달)
    private String fileUrl;

    // 파일 크기 (bytes)
    private long fileSize;
}
