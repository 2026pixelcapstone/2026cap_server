package com.expansion.server.domain.asset.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AssetCommentCreateRequest {

    @NotBlank
    @Size(max = 1000)
    private String content;

    private Long parentId;

    // 리뷰 별점(1~5). null이면 일반 댓글. 최상위 댓글에만 유효(대댓글이면 무시).
    @Min(1)
    @Max(5)
    private Integer rating;
}
