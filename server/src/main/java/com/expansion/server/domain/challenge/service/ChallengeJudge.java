package com.expansion.server.domain.challenge.service;

import com.expansion.server.domain.challenge.entity.Challenge;
import com.expansion.server.domain.challenge.entity.ChallengeEntry;
import com.expansion.server.domain.challenge.repository.ChallengeEntryRepository;
import com.expansion.server.domain.challenge.repository.ChallengeRepository;
import com.expansion.server.domain.notification.entity.NotificationType;
import com.expansion.server.domain.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 챌린지 우승 판정 — 판정 규칙은 이 클래스 한 곳에만 둔다(나중에 투표 방식으로 바꿀 때 여기만 수정).
 * 현재 규칙: 공개 참가작 중 '마감 전에 받은 좋아요'가 많은 순 톱3, 동률은 먼저 올린 작품.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeJudge {

    static final int WINNERS = 3;

    private final ChallengeRepository challengeRepository;
    private final ChallengeEntryRepository challengeEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 한 챌린지 판정. 행 잠금 + judgedAt 확인으로 여러 번 불려도 한 번만 처리된다. */
    @Transactional
    public void judge(Long challengeId) {
        Challenge challenge = challengeRepository.findByIdForUpdate(challengeId).orElse(null);
        if (challenge == null || challenge.isJudged()) return;
        LocalDateTime now = LocalDateTime.now();
        if (challenge.getEndsAt().isAfter(now)) return;   // 아직 진행 중

        List<Object[]> ranked = challengeEntryRepository.rankForJudging(
                challengeId, challenge.getEndsAt(), WINNERS);

        Map<Long, ChallengeEntry> entries = challengeEntryRepository.findAllById(
                        ranked.stream().map(r -> ((Number) r[0]).longValue()).toList())
                .stream().collect(Collectors.toMap(ChallengeEntry::getEntryId, Function.identity()));

        int rank = 0;
        for (Object[] row : ranked) {
            ChallengeEntry entry = entries.get(((Number) row[0]).longValue());
            if (entry == null) continue;
            rank++;
            entry.markRank(rank, ((Number) row[1]).intValue());
            eventPublisher.publishEvent(NotificationEvent.system(
                    entry.getUserId(), NotificationType.CHALLENGE_WINNER, challengeId,
                    String.format(NotificationType.CHALLENGE_WINNER.getTitleTemplate(), challenge.getTopic(), rank)));
        }
        challenge.markJudged(rank, now);
        log.info("[CHALLENGE] 판정 완료 — challengeId={}, topic={}, winners={}", challengeId, challenge.getTopic(), rank);
    }
}
