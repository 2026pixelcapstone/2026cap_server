package com.expansion.server.global.security.abuseipdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AbuseIPDB 차단 설정 (application.yml 의 abuseipdb.* 바인딩).
 *
 * @param enabled         false면 조회 자체를 하지 않고 모든 요청 통과(로컬 기본).
 * @param apiKey          AbuseIPDB Check API 키. enabled=true일 때만 의미 있음.
 * @param scoreThreshold  abuseConfidenceScore(0~100)가 이 값 이상이면 차단. 클수록 보수적(오탐↓).
 * @param cacheTtlSeconds IP→점수 캐시 유지 시간(초). 같은 IP 재조회를 막아 무료 쿼터(1,000/일) 보호.
 * @param timeoutMs       Check API 호출 타임아웃(ms). 초과하면 fail-open(통과).
 *
 * 신고(Report) — 로그인 brute-force로 판단된 IP를 AbuseIPDB에 신고(커뮤니티 기여).
 * @param reportEnabled        false면 신고 안 함. enabled(Check)와 독립으로 켤 수 있음(api-key 공유).
 * @param loginFailThreshold   윈도 내 로그인 실패가 이 횟수 이상 누적되면 신고.
 * @param failWindowMinutes    실패 카운트를 유지하는 시간 창(분).
 * @param reportCooldownMinutes 같은 IP를 다시 신고하지 않는 쿨다운(분) — 중복 신고 방지.
 */
@ConfigurationProperties(prefix = "abuseipdb")
public record AbuseIpdbProperties(
        boolean enabled,
        String apiKey,
        int scoreThreshold,
        long cacheTtlSeconds,
        long timeoutMs,
        boolean reportEnabled,
        int loginFailThreshold,
        long failWindowMinutes,
        long reportCooldownMinutes
) {
    // 잘못된 설정(0 이하)으로 첫 실패부터 신고되거나 캐시 TTL이 0이 되는 것을 기동 시 차단(fail-fast).
    public AbuseIpdbProperties {
        if (loginFailThreshold < 1) {
            throw new IllegalArgumentException("abuseipdb.login-fail-threshold must be >= 1");
        }
        if (failWindowMinutes < 1) {
            throw new IllegalArgumentException("abuseipdb.fail-window-minutes must be >= 1");
        }
        if (reportCooldownMinutes < 1) {
            throw new IllegalArgumentException("abuseipdb.report-cooldown-minutes must be >= 1");
        }
    }
}
