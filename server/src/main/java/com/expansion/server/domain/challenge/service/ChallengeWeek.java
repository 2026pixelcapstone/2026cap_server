package com.expansion.server.domain.challenge.service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/**
 * 챌린지 한 주 = 월 00:00 KST ~ 다음 월 00:00 KST(미포함).
 * 경계는 KST로 계산하되, 저장·비교용 LocalDateTime은 다른 컬럼(작품·좋아요 created_at)과 같은
 * 서버 기본 시간대로 바꿔서 돌려준다 → 서버 시간대가 KST가 아니어도 비교가 어긋나지 않음.
 */
public final class ChallengeWeek {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private ChallengeWeek() {}

    public record Period(LocalDateTime startsAt, LocalDateTime endsAt) {}

    /** now가 속한 주 */
    public static Period of(Instant now) {
        ZonedDateTime kstStart = now.atZone(KST).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(KST);
        ZonedDateTime kstEnd = kstStart.plusWeeks(1);
        return new Period(toServerTime(kstStart), toServerTime(kstEnd));
    }

    public static Period current() {
        return of(Instant.now());
    }

    private static LocalDateTime toServerTime(ZonedDateTime t) {
        return t.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
