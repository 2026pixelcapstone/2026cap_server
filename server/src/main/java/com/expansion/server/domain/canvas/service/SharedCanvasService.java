package com.expansion.server.domain.canvas.service;

import com.expansion.server.domain.canvas.dto.CanvasEvent;
import com.expansion.server.domain.canvas.dto.CanvasPixelRequest;
import com.expansion.server.domain.canvas.dto.CanvasPixelResponse;
import com.expansion.server.domain.canvas.dto.CanvasSnapshotResponse;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.domain.user.service.ActiveUserLock;
import com.expansion.server.domain.user.service.EmailVerificationGuard;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 공동 픽셀 캔버스(C-3).
 * 찍기 = ① 사용자 행 잠금·활성 확인(탈퇴와 한 줄로 — ActiveUserLock) ② 열린·만료 안 된 시즌 행 잠금(없으면 409)
 *        ③ 잠금을 다 잡은 뒤 쿨다운 한 줄 원자적 갱신(조건 안 맞으면 0행 → 429, 전체 롤백) ④ 칸 1바이트·version 갱신 + 기록
 *        ⑤ 커밋 뒤 /topic/canvas 로 PIXEL 이벤트.
 * 시각: 쿨다운 = (clock_timestamp() AT TIME ZONE 'UTC') — 잠금 대기 뒤의 실제 시각, 세션 시간대·일광 절약 시간과 무관.
 *       시즌 경계 = KST 벽시계(CanvasSeasonPeriod), 만료 비교 = (now() AT TIME ZONE 'Asia/Seoul').
 * 이벤트는 이 서버의 인메모리 브로커로만 감 — 서버 1대 전제(채팅 접속 표시와 같음). 여러 대면 브로커 릴레이 필요.
 * 시즌 행 잠금이 찍기를 한 줄로 세움 → version이 칸 이벤트 순서(화면은 칸별로 더 큰 version만 반영).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedCanvasService {

    public static final String TOPIC = "/topic/canvas";

    private final EntityManager em;
    private final UserRepository userRepository;
    private final ActiveUserLock activeUserLock;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String NOW_UTC = "(clock_timestamp() AT TIME ZONE 'UTC')";
    private static final String OPEN_SEASON_FOR_UPDATE =
            "SELECT season_id FROM canvas_seasons WHERE closed_at IS NULL AND ends_at > (now() AT TIME ZONE 'Asia/Seoul') FOR UPDATE";

    @Value("${canvas.cooldown-seconds:300}")
    private int cooldownSeconds;

    @Transactional(readOnly = true)
    public CanvasSnapshotResponse snapshot(Long userId) {
        List<?> rows = em.createNativeQuery(
                "SELECT season_id, encode(board, 'base64'), version, ends_at FROM canvas_seasons WHERE closed_at IS NULL")
                .getResultList();
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.CANVAS_NOT_READY);
        }
        Object[] r = (Object[]) rows.get(0);
        String board = ((String) r[1]).replaceAll("\\s", "");   // Postgres base64는 76자마다 줄바꿈을 넣음
        return new CanvasSnapshotResponse(((Number) r[0]).longValue(), SharedCanvasPalette.SIZE, SharedCanvasPalette.COLORS,
                board, ((Number) r[2]).longValue(), CanvasSeasonPeriod.toOffset(toLdt(r[3])), cooldownSeconds, cooldownRemainingMs(userId));
    }

    @Transactional
    public CanvasPixelResponse place(Long userId, CanvasPixelRequest req) {
        activeUserLock.lockAll(userId);                               // 탈퇴와 한 줄로(탈퇴가 지운 쿨다운 행을 다시 만들지 않게)
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        EmailVerificationGuard.assertVerified(user);

        List<?> open = em.createNativeQuery(OPEN_SEASON_FOR_UPDATE).getResultList();
        if (open.isEmpty()) {
            throw new CustomException(ErrorCode.CANVAS_NOT_READY);   // 시즌 없음·만료(교체 전)
        }
        long seasonId = ((Number) open.get(0)).longValue();

        // 잠금을 다 잡은 뒤의 실제 시각으로 쿨다운 판정(대기 시간만큼 일찍 풀리지 않게)
        int ok = em.createNativeQuery(
                "INSERT INTO canvas_cooldowns (user_id, last_placed_at) VALUES (:u, " + NOW_UTC + ") "
                        + "ON CONFLICT (user_id) DO UPDATE SET last_placed_at = " + NOW_UTC + " "
                        + "WHERE canvas_cooldowns.last_placed_at <= " + NOW_UTC + " - make_interval(secs => :s)")
                .setParameter("u", userId).setParameter("s", cooldownSeconds)
                .executeUpdate();
        if (ok == 0) {
            throw new CustomException(ErrorCode.CANVAS_COOLDOWN);
        }
        int x = req.x(), y = req.y(), color = req.color();
        em.createNativeQuery("UPDATE canvas_seasons SET board = set_byte(board, :i, :c), version = version + 1 WHERE season_id = :id")
                .setParameter("i", y * SharedCanvasPalette.SIZE + x).setParameter("c", color).setParameter("id", seasonId)
                .executeUpdate();
        long version = currentVersion(seasonId);
        em.createNativeQuery("INSERT INTO canvas_placements (season_id, user_id, x, y, color, version, placed_at) "
                        + "VALUES (:s, :u, :x, :y, :c, :v, " + NOW_UTC + ")")
                .setParameter("s", seasonId).setParameter("u", userId).setParameter("x", x).setParameter("y", y)
                .setParameter("c", color).setParameter("v", version)
                .executeUpdate();

        afterCommit(CanvasEvent.pixel(seasonId, x, y, color, version));
        return new CanvasPixelResponse(seasonId, x, y, color, version, cooldownSeconds * 1000L);
    }

    /**
     * 관리자 되돌리기 — 이번 시즌에서 이 사용자가 찍은 칸을 기록에서 빼고(reverted, 행은 남김) 칸마다 남은 마지막 기록으로 판을 다시 만든다
     * (칸별 마지막 1줄만 조회 — 최대 4,096줄, 기록이 많아도 메모리·잠금 시간이 늘지 않게).
     * 시즌 행 잠금으로 찍기와 한 줄로 섬. 끝나면 REFRESH(화면은 스냅샷을 다시 받음).
     */
    @Transactional
    public Map<String, Object> revertUser(Long targetUserId) {
        List<?> open = em.createNativeQuery(OPEN_SEASON_FOR_UPDATE).getResultList();
        if (open.isEmpty()) {
            throw new CustomException(ErrorCode.CANVAS_NOT_READY);
        }
        long seasonId = ((Number) open.get(0)).longValue();
        int reverted = em.createNativeQuery(
                "UPDATE canvas_placements SET reverted = TRUE WHERE season_id = :s AND user_id = :u AND reverted = FALSE")
                .setParameter("s", seasonId).setParameter("u", targetUserId).executeUpdate();
        byte[] board = new byte[SharedCanvasPalette.SIZE * SharedCanvasPalette.SIZE];
        List<?> rows = em.createNativeQuery(
                "SELECT DISTINCT ON (x, y) x, y, color FROM canvas_placements WHERE season_id = :s AND reverted = FALSE "
                        + "ORDER BY x, y, placement_id DESC")
                .setParameter("s", seasonId).getResultList();
        for (Object o : rows) {
            Object[] p = (Object[]) o;
            board[((Number) p[1]).intValue() * SharedCanvasPalette.SIZE + ((Number) p[0]).intValue()] = ((Number) p[2]).byteValue();
        }
        em.createNativeQuery("UPDATE canvas_seasons SET board = decode(:hex, 'hex'), version = version + 1 WHERE season_id = :id")
                .setParameter("hex", hex(board)).setParameter("id", seasonId).executeUpdate();
        long version = currentVersion(seasonId);
        afterCommit(CanvasEvent.refresh(seasonId, version));
        log.info("[CANVAS] 관리자 되돌리기 — seasonId={}, userId={}, 뺀 칸 {}개, version={}", seasonId, targetUserId, reverted, version);
        return Map.of("seasonId", seasonId, "reverted", reverted, "version", version);
    }

    private long currentVersion(long seasonId) {
        return ((Number) em.createNativeQuery("SELECT version FROM canvas_seasons WHERE season_id = :id")
                .setParameter("id", seasonId).getSingleResult()).longValue();
    }

    long cooldownRemainingMs(Long userId) {
        if (userId == null) return 0;
        List<?> r = em.createNativeQuery(
                "SELECT GREATEST(0, EXTRACT(EPOCH FROM (last_placed_at + make_interval(secs => :s) - " + NOW_UTC + ")) * 1000) "
                        + "FROM canvas_cooldowns WHERE user_id = :u")
                .setParameter("s", cooldownSeconds).setParameter("u", userId).getResultList();
        return r.isEmpty() ? 0 : (long) Math.ceil(((Number) r.get(0)).doubleValue());
    }

    /** 커밋된 뒤에만 이벤트를 보냄(롤백된 찍기가 화면에 나가지 않게) */
    void afterCommit(CanvasEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messagingTemplate.convertAndSend(TOPIC, event);
                } catch (Exception e) {
                    log.warn("[CANVAS] 이벤트 전송 실패 — {}", event, e);   // 화면은 재연결·새로고침 때 스냅샷으로 맞춰짐
                }
            }
        });
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) sb.append(String.format("%02x", v));
        return sb.toString();
    }

    static LocalDateTime toLdt(Object o) {
        if (o instanceof LocalDateTime t) return t;
        if (o instanceof Timestamp t) return t.toLocalDateTime();
        return null;
    }
}
