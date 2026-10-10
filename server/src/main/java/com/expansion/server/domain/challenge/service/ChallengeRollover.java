package com.expansion.server.domain.challenge.service;

import com.expansion.server.domain.challenge.entity.Challenge;
import com.expansion.server.domain.challenge.entity.ChallengeTopic;
import com.expansion.server.domain.challenge.repository.ChallengeRepository;
import com.expansion.server.domain.challenge.repository.ChallengeTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 주간 교체 작업 — ①매주 월 00:00 KST(cron) ②서버 기동 직후(배포 재시작 등으로 놓친 것 보충) 두 곳에서 같은 run().
 * 순서: 마감된 미판정 챌린지 판정 → 이번 주 챌린지가 없으면 주제 풀에서 꺼내 생성.
 * 여러 번 돌아도 안전(판정은 행 잠금+judgedAt, 생성은 UNIQUE(starts_at)). 실패는 로그만 — 서버 기동을 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeRollover {

    private final ChallengeRepository challengeRepository;
    private final ChallengeTopicRepository challengeTopicRepository;
    private final ChallengeJudge challengeJudge;
    private final TransactionTemplate transactionTemplate;

    // zone 필수 — 빠지면 서버 시간대 기준으로 돈다
    @Scheduled(cron = "${challenge.rollover-cron:0 0 0 * * MON}", zone = "Asia/Seoul")
    public void onSchedule() {
        run("schedule");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run("startup");
    }

    public void run(String trigger) {
        for (Long id : challengeRepository.findIdsDueForJudging(LocalDateTime.now())) {
            try {
                challengeJudge.judge(id);
            } catch (Exception e) {
                log.error("[CHALLENGE] 판정 실패 — trigger={}, challengeId={}", trigger, id, e);
            }
        }
        try {
            createCurrentIfAbsent();
        } catch (Exception e) {
            log.error("[CHALLENGE] 이번 주 챌린지 생성 실패 — trigger={}", trigger, e);
        }
    }

    private void createCurrentIfAbsent() {
        ChallengeWeek.Period week = ChallengeWeek.current();
        transactionTemplate.executeWithoutResult(status -> {
            if (challengeRepository.findByStartsAt(week.startsAt()).isPresent()) return;
            ChallengeTopic topic = challengeTopicRepository.findNextForUpdate().orElse(null);
            if (topic == null) {
                log.warn("[CHALLENGE] 주제 풀이 비어 있어 이번 주 챌린지를 만들지 못함");
                return;
            }
            topic.markUsed(LocalDateTime.now());
            Challenge created = challengeRepository.saveAndFlush(
                    new Challenge(topic.getTopic(), topic.getDescription(), week.startsAt(), week.endsAt()));
            log.info("[CHALLENGE] 이번 주 챌린지 생성 — challengeId={}, topic={}", created.getChallengeId(), created.getTopic());
        });
    }
}
