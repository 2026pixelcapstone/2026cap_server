package com.expansion.server.domain.canvas.service;

import java.time.*;

/**
 * 공동 캔버스 시즌 = 분기(1·4·7·10월 1일 00:00 KST ~ 다음 분기 1일 00:00 KST, 끝은 미포함).
 * 경계는 KST로 계산하고, 저장·비교용 LocalDateTime은 서버 기본 시간대로 바꿔서 돌려준다(ChallengeWeek와 같은 방식).
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
        return new Period(toServerTime(start), toServerTime(end));
    }

    public static Period current() {
        return of(Instant.now());
    }

    private static LocalDateTime toServerTime(ZonedDateTime t) {
        return t.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
