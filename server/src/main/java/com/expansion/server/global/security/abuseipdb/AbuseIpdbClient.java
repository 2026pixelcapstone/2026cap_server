package com.expansion.server.global.security.abuseipdb;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * AbuseIPDB Check API 호출 + 결과 캐시.
 *
 * <p>동작 흐름:
 * <ol>
 *   <li>먼저 인메모리 캐시(Caffeine)에서 IP의 점수를 찾는다 → 있으면 그대로 반환(API 호출 0회).</li>
 *   <li>캐시에 없으면 AbuseIPDB Check API를 1회 호출하고 결과를 캐시에 적재한다.</li>
 *   <li>호출이 실패(타임아웃·네트워크·4xx/5xx)하면 -1을 반환한다 = "조회 불가" 신호 →
 *       필터가 fail-open(통과)으로 처리한다. 실패는 캐시하지 않아 다음 요청에서 재시도된다.</li>
 * </ol>
 *
 * <p>캐시가 핵심인 이유: 무료 티어는 Check 1,000회/일 제한이라, 같은 IP가 반복 요청해도
 * 매번 외부 API를 때리면 금세 쿼터가 소진된다. cache-ttl-seconds 동안은 1회만 조회한다.
 */
@Slf4j
@Component
public class AbuseIpdbClient {

    /** AbuseIPDB Check 엔드포인트. ipAddress + maxAgeInDays(최근 N일 신고만 집계) 쿼리. */
    private static final String CHECK_URL = "https://api.abuseipdb.com/api/v2/check";
    private static final int MAX_AGE_IN_DAYS = 90;
    private static final int SCORE_UNAVAILABLE = -1;

    private final AbuseIpdbProperties props;
    private final RestClient restClient;
    private final Cache<String, Integer> scoreCache;

    public AbuseIpdbClient(AbuseIpdbProperties props) {
        this.props = props;

        // 타임아웃을 짧게(기본 2초) 둬서 평판 서버가 느릴 때 요청 전체가 지연되지 않게 한다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.timeoutMs());
        factory.setReadTimeout((int) props.timeoutMs());
        this.restClient = RestClient.builder().requestFactory(factory).build();

        this.scoreCache = Caffeine.newBuilder()
                .maximumSize(10_000)   // 메모리 폭주 방지(가장 오래된 항목부터 축출)
                .expireAfterWrite(Duration.ofSeconds(props.cacheTtlSeconds()))
                .build();
    }

    /**
     * IP의 abuseConfidenceScore(0~100)를 반환. 조회 불가 시 -1.
     * 점수가 높을수록 악성 신고가 많이 누적된 IP.
     */
    public int getAbuseScore(String ip) {
        Integer cached = scoreCache.getIfPresent(ip);
        if (cached != null) {
            return cached;
        }

        try {
            String url = CHECK_URL
                    + "?ipAddress=" + URLEncoder.encode(ip, StandardCharsets.UTF_8)
                    + "&maxAgeInDays=" + MAX_AGE_IN_DAYS;

            Map<?, ?> body = restClient.get()
                    .uri(URI.create(url))
                    .header("Key", props.apiKey())          // AbuseIPDB는 헤더 'Key'로 인증
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            // 응답 형태: { "data": { "abuseConfidenceScore": 0~100, ... } }
            if (body != null
                    && body.get("data") instanceof Map<?, ?> data
                    && data.get("abuseConfidenceScore") instanceof Number score) {
                int value = score.intValue();
                scoreCache.put(ip, value);   // 성공만 캐시 → 실패는 다음에 재시도
                return value;
            }
            log.warn("AbuseIPDB 응답에서 점수를 파싱하지 못함 — ip={}, body={}", ip, body);
            return SCORE_UNAVAILABLE;

        } catch (Exception e) {
            // 타임아웃·네트워크 오류·쿼터 초과(429)·키 오류(401) 등 → fail-open
            log.warn("AbuseIPDB 조회 실패(통과 처리) — ip={}, cause={}", ip, e.toString());
            return SCORE_UNAVAILABLE;
        }
    }
}
