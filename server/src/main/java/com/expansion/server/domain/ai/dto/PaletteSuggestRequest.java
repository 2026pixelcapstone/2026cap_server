package com.expansion.server.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 색 팔레트 추천 요청 (에디터 AI 탭 — 내 작업물 색 추천).
 *
 * @param imageBase64   현재 캔버스(프레임)를 PNG로 뽑은 base64. data URL 접두어(data:image/png;base64,)는
 *                      있어도 되고 없어도 됨(서비스에서 제거). 이미지 이해용 필수 입력.
 * @param currentColors 현재 작업물에 사용된 색(hex #RRGGBB) 목록. 없으면 null/빈 리스트 허용.
 *                      과대 요청·잘못된 형식 방어를 위해 개수·형식을 제한한다.
 * @param description   원하는 느낌/컨셉 자연어(예: "숲속 밤, 아늑한"). 선택. 과대 요청 방어로 길이 제한.
 */
public record PaletteSuggestRequest(
        @NotBlank(message = "이미지가 필요합니다.")
        String imageBase64,

        @Size(max = 64, message = "색상은 최대 64개까지 전달할 수 있습니다.")
        List<@Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다.") String> currentColors,

        @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
        String description
) {
}
