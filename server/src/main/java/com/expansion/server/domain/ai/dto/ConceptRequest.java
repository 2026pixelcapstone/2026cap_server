package com.expansion.server.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 컨셉 도우미 요청 (에디터 AI 탭 — 기능3).
 * 자연어 컨셉 설명 한 문장으로 색 팔레트 + 관련 작품을 추천받는다.
 *
 * @param description 만들고 싶은 컨셉 설명(예: "한밤중 네온이 빛나는 사이버펑크 도시 골목").
 */
public record ConceptRequest(
        @NotBlank(message = "컨셉 설명을 입력해 주세요.")
        @Size(max = 500, message = "컨셉 설명은 500자 이하여야 합니다.")
        String description
) {
}
