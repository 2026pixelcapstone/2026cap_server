package com.expansion.server.global.security.abuseipdb;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * AbuseIPDB 기반 악성 IP 차단 필터.
 *
 * <p><b>필터 체인 위치</b>: JwtFilter <i>뒤</i>에 등록(SecurityConfig). JwtFilter가 먼저
 * SecurityContext에 인증 정보를 채우므로, 여기서 "이미 로그인한 요청인지"를 판별할 수 있다.
 *
 * <p><b>요청 1건당 판정 순서</b>:
 * <ol>
 *   <li>{@link #shouldNotFilter}에서 비활성/preflight/정적·문서·헬스·WS 요청을 먼저 걸러 통과.</li>
 *   <li>이미 JWT 인증된(로그인) 요청이면 신뢰 → 조회 없이 통과(무료 쿼터·레이턴시 절약).</li>
 *   <li>실제 클라이언트 IP를 구한다(CF-Connecting-IP → X-Forwarded-For → remoteAddr).</li>
 *   <li>사설/루프백 IP면 조회 의미 없으므로 통과.</li>
 *   <li>AbuseIPDB 점수를 조회 → 임계값 이상이면 403, 미만/조회불가(-1)면 통과(fail-open).</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AbuseIpFilter extends OncePerRequestFilter {

    /** Cloudflare 터널 뒤에서 실제 방문자 IP가 담겨 오는 헤더(최우선). */
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** IPv4 점표기(각 옥텟 0~255)만 매칭. getByName에 호스트명이 새지 않도록 사전 필터. */
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private final AbuseIpdbProperties props;
    private final AbuseIpdbClient client;

    /** 비활성이거나 검사할 필요 없는 요청은 아예 필터를 건너뛴다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.enabled()) {
            return true;   // abuseipdb.enabled=false(로컬 기본) → 전부 통과
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;   // CORS preflight는 본요청 아님
        }
        String uri = request.getRequestURI();
        return uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/ws")          // WebSocket 핸드셰이크
                || uri.startsWith("/actuator");   // 헬스체크
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1) 이미 로그인(JWT 인증)한 요청은 신뢰 → 조회 스킵
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) 실제 클라이언트 IP
        String ip = resolveClientIp(request);

        // 3) 사설/루프백/파싱불가 IP는 평판 조회가 무의미 → 통과
        if (ip == null || isPrivateOrLoopback(ip)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4) 평판 조회 후 임계값 이상이면 차단. -1(조회불가)은 fail-open.
        int score = client.getAbuseScore(ip);
        if (score >= props.scoreThreshold()) {
            log.warn("AbuseIPDB 차단 — ip={}, score={}(>= {}), uri={}",
                    ip, score, props.scoreThreshold(), request.getRequestURI());
            writeForbidden(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * CF-Connecting-IP → X-Forwarded-For 첫 항목 → remoteAddr 순으로 실제 IP 결정.
     *
     * <p>각 헤더값은 <b>유효한 IP 리터럴일 때만</b> 신뢰한다(아니면 다음 소스로 폴백).
     * 헤더는 클라이언트가 임의로 넣을 수 있으므로, 호스트명/쓰레기값을 그대로 받으면
     * (a) {@link #isPrivateOrLoopback}에서 DNS 조회가 일어나거나 (b) 조회 우회에 악용될 수 있다.
     *
     * <p><b>전제(인프라)</b>: 백엔드는 cloudflared 터널을 통해서만 외부 노출되며 8080 직접 접근은 차단된다.
     * Cloudflare 엣지는 CF-Connecting-IP를 실제 클라이언트 IP로 <i>덮어쓰므로</i> 엣지 경유 요청에서는
     * 이 헤더를 위조할 수 없다. (직접 노출 구조로 바뀌면 신뢰 프록시 대역 화이트리스트가 추가로 필요.)
     */
    private String resolveClientIp(HttpServletRequest request) {
        String cf = request.getHeader(CF_CONNECTING_IP);
        if (isValidIpLiteral(cf)) {
            return cf.trim();
        }
        String xff = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(xff)) {
            // "client, proxy1, proxy2" 형태 → 맨 앞이 원 클라이언트
            String first = xff.split(",")[0].trim();
            if (isValidIpLiteral(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();   // 컨테이너가 채우는 신뢰값(항상 유효 IP)
    }

    /**
     * 문자열이 유효한 IP 리터럴인지(DNS 조회 없이). 호스트명·쓰레기값은 false.
     *
     * <p>IPv4 점표기(엄격 정규식) 또는 IPv6 형태(hex+콜론)만 {@link InetAddress#getByName}에 넘긴다.
     * getByName은 인자가 IP 리터럴이면 DNS 조회를 하지 않으므로, 사전 형태검증으로 호스트명을 걸러내면
     * "공격자 제어 문자열 → 블로킹 DNS 조회" 위험이 사라진다.
     */
    private boolean isValidIpLiteral(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        String s = ip.trim();
        boolean looksLikeIp = IPV4.matcher(s).matches()
                || (s.indexOf(':') >= 0 && s.matches("^[0-9a-fA-F:]+$"));   // IPv6 약식
        if (!looksLikeIp) {
            return false;
        }
        try {
            InetAddress.getByName(s);   // 형태가 리터럴로 확정된 값만 → DNS 조회 없음
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * 내부망/루프백 IP 여부. 호출 전 {@link #isValidIpLiteral}로 리터럴이 보장된 값만 들어오므로
     * getByName은 DNS 조회를 하지 않는다. 방어적으로 파싱 실패는 "조회 불가"로 보고 통과(true).
     */
    private boolean isPrivateOrLoopback(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return true;
        }
    }

    /** ApiResponse(success=false) 포맷과 동일한 403 JSON 직접 작성(필터는 ExceptionHandler를 안 탐). */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"비정상적인 접근으로 차단되었습니다.\"}");
    }
}
