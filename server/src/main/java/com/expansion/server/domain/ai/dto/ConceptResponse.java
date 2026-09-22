package com.expansion.server.domain.ai.dto;

import com.expansion.server.domain.gallery.dto.GalleryPostSummary;

import java.util.List;

/**
 * AI 컨셉 도우미 결과 (기능3).
 *
 * @param colors       추천 색(hex #RRGGBB) 목록 — 기능1·2와 동일하게 스와치로 표시/추가.
 * @param keywords     Gemini가 컨셉에서 뽑은 검색 키워드 — 프론트 "이 태그로 찾았어요" 표시용.
 * @param relatedPosts 위 키워드로 우리 갤러리에서 찾은 관련 작품(최대 N개). 매칭 0건이면 빈 목록.
 */
public record ConceptResponse(
        List<String> colors,
        List<String> keywords,
        List<GalleryPostSummary> relatedPosts
) {
}
