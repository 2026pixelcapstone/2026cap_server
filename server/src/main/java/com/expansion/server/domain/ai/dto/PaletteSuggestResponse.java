package com.expansion.server.domain.ai.dto;

import java.util.List;

/**
 * AI 색 팔레트 추천 결과.
 *
 * @param colors 추천 색(hex #RRGGBB) 목록. 프론트는 스와치로 표시하고, 클릭 시 팔레트에 추가한다.
 */
public record PaletteSuggestResponse(
        List<String> colors
) {
}
