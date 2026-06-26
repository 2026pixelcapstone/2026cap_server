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
 */
@ConfigurationProperties(prefix = "abuseipdb")
public record AbuseIpdbProperties(
        boolean enabled,
        String apiKey,
        int scoreThreshold,
        long cacheTtlSeconds,
        long timeoutMs
) {
}
