package com.expansion.server.domain.canvas.dto;

/** 찍기 성공 — 이 칸이 반영된 판 version과 다음 찍기까지 남은 시간 */
public record CanvasPixelResponse(Long seasonId, int x, int y, int color, long version, long cooldownRemainingMs) {}
