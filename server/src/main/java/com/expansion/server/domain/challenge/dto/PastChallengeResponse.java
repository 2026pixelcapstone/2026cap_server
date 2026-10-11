package com.expansion.server.domain.challenge.dto;

import com.expansion.server.domain.gallery.dto.GalleryPostSummary;

import java.time.OffsetDateTime;
import java.util.List;

/** 판정 끝난 지난 챌린지 + 톱3. winners는 판정 때 매긴 순위 수만큼(0~3). */
public record PastChallengeResponse(
        Long challengeId,
        String topic,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        List<Winner> winners
) {
    /** post == null 이면 삭제·비공개로 '볼 수 없는 작품' */
    public record Winner(int rank, int likeCount, GalleryPostSummary post) {}
}
