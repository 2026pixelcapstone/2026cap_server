package com.expansion.server.domain.canvas.service;

import com.expansion.server.domain.canvas.dto.CanvasEvent;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 시즌 교체 — ①매달 1일 00:00 KST(cron, 분기 첫 달이 아니면 할 일 없음) ②서버 기동 직후(놓친 교체 보충). ChallengeRollover와 같은 방식.
 * 열린 시즌이 지금 분기가 아니면 닫고, 지금 분기 시즌을 빈 판으로 연다. 여러 번 돌아도 안전(UNIQUE starts_at·열린 시즌 하나 인덱스).
 * 실패는 로그만 — 서버 기동을 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasSeasonRollover {

    private final EntityManager em;
    private final TransactionTemplate transactionTemplate;
    private final SharedCanvasService canvasService;

    @Scheduled(cron = "${canvas.rollover-cron:0 0 0 1 * *}", zone = "Asia/Seoul")
    public void onSchedule() {
        run("schedule");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run("startup");
    }

    public void run(String trigger) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                CanvasSeasonPeriod.Period p = CanvasSeasonPeriod.current();
                List<?> open = em.createNativeQuery(
                        "SELECT season_id, starts_at FROM canvas_seasons WHERE closed_at IS NULL FOR UPDATE").getResultList();
                if (!open.isEmpty()) {
                    Object[] r = (Object[]) open.get(0);
                    if (p.startsAt().equals(SharedCanvasService.toLdt(r[1]))) return;   // 이미 이번 분기
                    em.createNativeQuery("UPDATE canvas_seasons SET closed_at = LOCALTIMESTAMP WHERE season_id = :id")
                            .setParameter("id", ((Number) r[0]).longValue()).executeUpdate();
                    log.info("[CANVAS] 시즌 닫음 — seasonId={}", r[0]);
                }
                int created = em.createNativeQuery(
                        "INSERT INTO canvas_seasons (starts_at, ends_at, board) VALUES (:s, :e, decode(repeat('00', 4096), 'hex')) "
                                + "ON CONFLICT (starts_at) DO NOTHING")
                        .setParameter("s", p.startsAt()).setParameter("e", p.endsAt()).executeUpdate();
                if (created == 0) {   // 같은 분기 시즌이 이미 있는데 닫혀 있던 경우(수동 조작 등) — 다시 엶
                    em.createNativeQuery("UPDATE canvas_seasons SET closed_at = NULL WHERE starts_at = :s")
                            .setParameter("s", p.startsAt()).executeUpdate();
                }
                Number id = (Number) em.createNativeQuery("SELECT season_id FROM canvas_seasons WHERE starts_at = :s")
                        .setParameter("s", p.startsAt()).getSingleResult();
                canvasService.afterCommit(CanvasEvent.refresh(id.longValue(), 0));
                log.info("[CANVAS] 이번 분기 시즌 — seasonId={}, startsAt={}, trigger={}", id, p.startsAt(), trigger);
            });
        } catch (Exception e) {
            log.error("[CANVAS] 시즌 교체 실패 — trigger={}", trigger, e);
        }
    }
}
