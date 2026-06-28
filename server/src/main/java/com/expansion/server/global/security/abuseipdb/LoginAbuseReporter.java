package com.expansion.server.global.security.abuseipdb;

import com.expansion.server.global.util.ClientIpResolver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 로그인 brute-force로 판단된 IP를 AbuseIPDB에 신고(Report)한다.
 *
 * <p>동작:
 * <ol>
 *   <li>로그인 실패 시 IP별 카운트 +1 (failWindowMinutes 동안 유지).</li>
 *   <li>카운트가 loginFailThreshold 이상이면 AbuseIPDB에 신고 + 해당 IP를 쿨다운에 등록.</li>
 *   <li>쿨다운(reportCooldownMinutes) 중인 IP는 다시 신고하지 않는다(중복 신고 방지).</li>
 *   <li>로그인 성공 시 해당 IP의 실패 카운트를 초기화.</li>
 * </ol>
 *
 * <p>신고는 능동적 주장이라 신중해야 하므로 — 사설/루프백 IP는 제외하고,
 * report-enabled 플래그가 켜진 경우에만 동작한다(Check와 독립).
 */
@Slf4j
@Component
public class LoginAbuseReporter {

    // AbuseIPDB 카테고리: 18 = Brute-Force, 21 = Web App Attack
    private static final String CATEGORIES = "18,21";

    private final AbuseIpdbProperties props;
    private final AbuseIpdbClient client;
    private final ClientIpResolver clientIpResolver;

    private final Cache<String, Integer> failCounts;   // IP → 실패 횟수 (시간 창 동안)
    private final Cache<String, Boolean> cooldown;     // IP → 최근 신고됨 (쿨다운 동안)

    public LoginAbuseReporter(AbuseIpdbProperties props,
                              AbuseIpdbClient client,
                              ClientIpResolver clientIpResolver) {
        this.props = props;
        this.client = client;
        this.clientIpResolver = clientIpResolver;
        this.failCounts = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(Math.max(1, props.failWindowMinutes())))
                .build();
        this.cooldown = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(Math.max(1, props.reportCooldownMinutes())))
                .build();
    }

    /** 로그인 성공 — 해당 IP의 실패 누적을 초기화. */
    public void recordSuccess(String ip) {
        if (ip != null) {
            failCounts.invalidate(ip);
        }
    }

    /** 로그인 실패 — 카운트 누적, 임계 초과 시 신고. */
    public void recordFailure(String ip) {
        if (!props.reportEnabled()) {
            return;
        }
        if (ip == null || clientIpResolver.isPrivateOrLoopback(ip)) {
            return;   // 내부망/루프백은 신고 의미 없음
        }
        if (cooldown.getIfPresent(ip) != null) {
            return;   // 최근 신고한 IP는 재신고하지 않음
        }

        int count = failCounts.asMap().merge(ip, 1, Integer::sum);
        // 쿨다운 등록을 원자적으로(putIfAbsent) — 동시 실패 요청이 모두 신고하는 경쟁 방지.
        // 첫 등록에 성공한 스레드만 실제 신고한다.
        if (count >= props.loginFailThreshold()
                && cooldown.asMap().putIfAbsent(ip, Boolean.TRUE) == null) {
            client.report(ip, CATEGORIES,
                    "Repeated failed login attempts (" + count + ") detected on PixelPilot");
            failCounts.invalidate(ip);
            log.warn("로그인 brute-force 신고 — ip={}, 실패 {}회", ip, count);
        }
    }
}
