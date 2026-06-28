package com.expansion.server.global.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * 요청에서 실제 클라이언트 IP를 안전하게 추출한다.
 *
 * <p>AbuseIP 차단 필터와 로그인 brute-force 신고가 공통으로 쓴다(중복 제거).
 *
 * <p><b>전제(인프라)</b>: 백엔드는 cloudflared 터널을 통해서만 외부 노출되며 8080 직접 접근은 차단된다.
 * Cloudflare 엣지가 CF-Connecting-IP를 실제 클라이언트 IP로 덮어쓰므로 엣지 경유 요청에서는 위조 불가.
 * (직접 노출 구조로 바뀌면 신뢰 프록시 대역 화이트리스트가 추가로 필요.)
 */
@Component
public class ClientIpResolver {

    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** IPv4 점표기(각 옥텟 0~255)만 매칭. getByName에 호스트명이 새지 않도록 사전 필터. */
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    /**
     * CF-Connecting-IP → X-Forwarded-For 첫 항목 → remoteAddr 순으로 실제 IP 결정.
     * 각 헤더값은 <b>유효한 IP 리터럴일 때만</b> 신뢰한다(아니면 다음 소스로 폴백).
     */
    public String resolve(HttpServletRequest request) {
        String cf = request.getHeader(CF_CONNECTING_IP);
        if (isValidIpLiteral(cf)) {
            return cf.trim();
        }
        String xff = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();   // "client, proxy1, ..." → 맨 앞이 원 클라이언트
            if (isValidIpLiteral(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();   // 컨테이너가 채우는 신뢰값(항상 유효 IP)
    }

    /**
     * 문자열이 유효한 IP 리터럴인지(DNS 조회 없이). 호스트명·쓰레기값은 false.
     * IPv4 점표기/ IPv6 형태만 getByName에 넘긴다 → 리터럴이라 DNS 조회 안 함.
     */
    public boolean isValidIpLiteral(String ip) {
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
            InetAddress.getByName(s);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** 내부망/루프백 IP 여부. 리터럴이 아니면 "조회 불가"로 보고 통과(true). DNS 조회 없음. */
    public boolean isPrivateOrLoopback(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            // IPv6 ULA(fc00::/7) — isSiteLocalAddress()는 deprecated fec0::/10만 잡고 ULA는 못 잡음.
            if (addr instanceof Inet6Address && (addr.getAddress()[0] & 0xFE) == 0xFC) {
                return true;
            }
            return addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
