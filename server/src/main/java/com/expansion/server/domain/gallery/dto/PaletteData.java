package com.expansion.server.domain.gallery.dto;

import java.util.List;

/**
 * 팔레트 데이터 — .ppit / projects / gallery_posts.palette_data 공통 형태.
 * {@code {name?, colors[]}} 로 직렬화되어 palette_data(JSONB)에 저장된다.
 */
public record PaletteData(
        String name,           // 표시/재활용용 라벨 (선택)
        List<String> colors    // "#RRGGBB" 또는 "#RRGGBBAA"
) {}
