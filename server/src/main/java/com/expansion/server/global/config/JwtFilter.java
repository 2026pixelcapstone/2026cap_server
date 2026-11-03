package com.expansion.server.global.config;

import com.expansion.server.global.security.UserStatusChecker;
import com.expansion.server.global.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserStatusChecker userStatusChecker;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtUtil.isValid(token)
                && !userStatusChecker.isActive(jwtUtil.getUserId(token))) {
            // 탈퇴·정지 계정의 남은 토큰 — 인증하지 않음(익명 → 보호 API는 401, 갱신도 막혀 있어 로그아웃으로 이어짐)
            log.info("비활성 계정 토큰 거절 — userId={}, uri={}", jwtUtil.getUserId(token), request.getRequestURI());
        } else if (token != null && jwtUtil.isValid(token)) {
            Long userId = jwtUtil.getUserId(token);
            String role = jwtUtil.getRole(token);

            log.debug("JWT 인증 성공 — userId={}, role={}, method={}, uri={}",
                    userId, role, request.getMethod(), request.getRequestURI());

            var auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
        } else if (token != null) {
            log.warn("JWT 토큰 유효성 검사 실패 — method={}, uri={}",
                    request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
