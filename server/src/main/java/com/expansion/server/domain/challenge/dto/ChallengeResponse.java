package com.expansion.server.domain.challenge.dto;

import java.time.OffsetDateTime;

/**
 * 이번 주 챌린지. 기간은 KST 오프셋을 붙여 내려준다(endsAt은 미포함 경계 = 다음 주 월 00:00).
 * myEntryPostId = 로그인 사용자의 현재 참가작(없거나 비로그인이면 null).
 */
public record ChallengeResponse(
        Long challengeId,
        String topic,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        long entryCount,
        Long myEntryPostId
) {}
