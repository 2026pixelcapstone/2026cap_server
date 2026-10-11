package com.expansion.server.domain.challenge.dto;

import com.expansion.server.domain.challenge.entity.ChallengeTopic;

import java.time.LocalDateTime;

/** 관리자용 주제 항목 — usedAt == null 이면 아직 공개 전(예정) */
public record ChallengeTopicResponse(
        Long topicId,
        String topic,
        String description,
        int sortOrder,
        String source,
        String status,
        LocalDateTime usedAt,
        LocalDateTime createdAt
) {
    public static ChallengeTopicResponse of(ChallengeTopic t) {
        return new ChallengeTopicResponse(t.getTopicId(), t.getTopic(), t.getDescription(), t.getSortOrder(),
                t.getSource(), t.getStatus(), t.getUsedAt(), t.getCreatedAt());
    }
}
