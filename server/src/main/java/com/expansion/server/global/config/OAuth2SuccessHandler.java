package com.expansion.server.global.config;

import com.expansion.server.domain.user.dto.TokenResponse;
import com.expansion.server.domain.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 소셜 로그인 성공 후 우리 서비스 JWT를 발급해 프론트 콜백으로 리다이렉트.
 * 토큰 발급은 AuthService에 위임(refresh 토큰 DB 저장 포함).
 * ⚠️ 현재 토큰을 콜백 URL 쿼리로 전달 — 추후 일회용 코드/httpOnly 쿠키로 개선 예정(WORK_STATUS 참고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${oauth2.redirect-base-url:http://localhost:5173}")
    private String redirectBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        if (email == null) {
            log.error("[OAuth2] 인증 성공했으나 이메일 정보 없음");
            getRedirectStrategy().sendRedirect(request, response,
                    redirectBaseUrl + "/login?error=oauth");
            return;
        }

        TokenResponse tokens = authService.issueTokensForOAuth(email);

        // UriComponentsBuilder가 쿼리 값 인코딩 처리
        String redirectUrl = UriComponentsBuilder
                .fromUriString(redirectBaseUrl + "/oauth2/callback")
                .queryParam("accessToken", tokens.getAccessToken())
                .queryParam("refreshToken", tokens.getRefreshToken())
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
