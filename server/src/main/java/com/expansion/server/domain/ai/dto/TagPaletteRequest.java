package com.expansion.server.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 태그 기반 색 추천 요청 (에디터 AI 탭 — 태그로 색 찾기).
 * 이미지 없이 태그/키워드만으로 어울리는 색 팔레트를 제안받는다.
 *
 * @param tags 컨셉/키워드 목록(예: ["석양", "숲"]). 최소 1개, 과대 요청 방어로 개수·길이 제한.
 */
public record TagPaletteRequest(
        @NotEmpty(message = "태그를 하나 이상 입력해 주세요.")
        @Size(max = 10, message = "태그는 최대 10개까지입니다.")
        List<@NotBlank(message = "빈 태그는 사용할 수 없습니다.") @Size(max = 30, message = "태그는 30자 이하여야 합니다.") String> tags
) {
}
