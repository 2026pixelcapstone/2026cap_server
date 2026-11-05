package com.expansion.server.domain.canvas.service;

import java.util.List;

/** 공동 캔버스 고정 16색 — 색 번호 0~15. 0(흰색)이 빈 칸. 화면은 스냅샷 응답으로 받아 그린다. */
public final class SharedCanvasPalette {

    public static final int SIZE = 64;
    public static final List<String> COLORS = List.of(
            "#ffffff", "#c8c8c8", "#787878", "#1e1e1e",
            "#ff9fc4", "#e03131", "#f08c00", "#8a5a33",
            "#ffd43b", "#82c91e", "#2f9e44", "#22b8cf",
            "#1c7ed6", "#3b2fc9", "#be4bdb", "#6f2a8f");

    private SharedCanvasPalette() {}
}
