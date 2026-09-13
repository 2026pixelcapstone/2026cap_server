package com.expansion.server.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * AI 색 팔레트 추천 요청 (에디터 AI 탭 — 내 작업물 색 추천).
 *
 * @param imageBase64   현재 캔버스(프레임)를 PNG로 뽑은 base64. data URL 접두어(data:image/png;base64,)는
 *                      있어도 되고 없어도 됨(서비스에서 제거). 이미지 이해용 필수 입력.
 * @param currentColors 현재 작업물에 사용된 색(hex #RRGGBB) 목록. 없으면 null/빈 리스트 허용.
 * @param description   원하는 느낌/컨셉 자연어(예: "숲속 밤, 아늑한"). 선택.
 */
public record PaletteSuggestRequest(
        @NotBlank(message = "이미지가 필요합니다.")
        String imageBase64,
        List<String> currentColors,
        String description
) {
}
