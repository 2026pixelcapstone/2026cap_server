package com.expansion.server.domain.canvas.service;

import java.time.*;

/**
 * 공동 캔버스 시즌 = 분기(1·4·7·10월 1일 00:00 KST ~ 다음 분기 1일 00:00 KST, 끝은 미포함).
 * 저장·비교 값은 **KST 벽시계 LocalDateTime**(서버 JVM·DB 시간대와 무관 — CodeRabbit #74). 만료 비교는 SQL에서
 * (now() AT TIME ZONE 'Asia/Seoul')로 같은 기준을 씀. 응답에는 +09:00 오프셋을 붙여 절대 시각으로 내보낸다.
 */
public final class CanvasSeasonPeriod {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private CanvasSeasonPeriod() {}

    public record Period(LocalDateTime startsAt, LocalDateTime endsAt) {}

    public static Period of(Instant now) {
        LocalDate d = now.atZone(KST).toLocalDate();
        int firstMonth = ((d.getMonthValue() - 1) / 3) * 3 + 1;
        ZonedDateTime start = LocalDate.of(d.getYear(), firstMonth, 1).atStartOfDay(KST);
        ZonedDateTime end = start.plusMonths(3);
        return new Period(start.toLocalDateTime(), end.toLocalDateTime());
    }

    public static Period current() {
        return of(Instant.now());
    }

    /** KST 벽시계 값 → +09:00 오프셋 시각(응답용) */
    public static OffsetDateTime toOffset(LocalDateTime kstWallClock) {
        return kstWallClock == null ? null : kstWallClock.atZone(KST).toOffsetDateTime();
    }
}
