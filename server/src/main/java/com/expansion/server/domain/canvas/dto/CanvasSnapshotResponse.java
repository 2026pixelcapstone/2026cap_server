package com.expansion.server.domain.canvas.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/canvas 응답. board = 칸 4096개(행 우선)의 색 번호 바이트를 base64로.
 * cooldownRemainingMs = 로그인 사용자의 남은 쿨다운(비로그인·없음이면 0).
 */
public record CanvasSnapshotResponse(
        Long seasonId,
        int size,
        List<String> palette,
        String board,
        long version,
        LocalDateTime seasonEndsAt,
        int cooldownSeconds,
        long cooldownRemainingMs
) {}
