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

    /** CF-Connecting-IP → X-Forwarded-For 첫 항목 → remoteAddr 순으로 실제 IP 결정. */
    private String resolveClientIp(HttpServletRequest request) {
        String cf = request.getHeader(CF_CONNECTING_IP);
        if (StringUtils.hasText(cf)) {
            return cf.trim();
        }
        String xff = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(xff)) {
            // "client, proxy1, proxy2" 형태 → 맨 앞이 원 클라이언트
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 내부망/루프백 IP 여부. 파싱 실패도 "조회 불가"로 보고 통과(true). */
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
