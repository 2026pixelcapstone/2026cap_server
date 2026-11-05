package com.expansion.server.domain.canvas.dto;

/**
 * /topic/canvas 로 보내는 이벤트.
 * PIXEL = 칸 하나(x·y·color·version), REFRESH = 판을 다시 받아야 함(관리자 되돌리기·시즌 교체 — 화면은 스냅샷을 다시 조회).
 */
public record CanvasEvent(String type, Long seasonId, Integer x, Integer y, Integer color, long version) {

    public static CanvasEvent pixel(Long seasonId, int x, int y, int color, long version) {
        return new CanvasEvent("PIXEL", seasonId, x, y, color, version);
    }

    public static CanvasEvent refresh(Long seasonId, long version) {
        return new CanvasEvent("REFRESH", seasonId, null, null, null, version);
    }
}
