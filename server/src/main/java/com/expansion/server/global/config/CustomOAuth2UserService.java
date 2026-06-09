package com.expansion.server.global.config;

import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 소셜 로그인 시 사용자 프로비저닝.
 * - 동일 이메일 유저가 있으면 → 소셜 정보 연결 + 이메일 인증 처리(중복 계정 방지)
 * - 없으면 → 자동 가입(User + Profile, 닉네임 유니크화). 소셜 제공자가 이메일 검증하므로 email_verified=true.
 * 토큰 발급/리다이렉트는 OAuth2SuccessHandler가 담당하고, 여기서는 DB 매핑만.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId(); // "google"
        String email = oAuth2User.getAttribute("email");
        String socialId = oAuth2User.getAttribute("sub");

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"), "소셜 계정에서 이메일을 가져올 수 없습니다.");
        }

        userRepository.findByEmail(email).ifPresentOrElse(
                user -> user.linkSocial(provider, socialId),            // 기존 유저 → 소셜 연결
                () -> createSocialUser(email, provider, socialId)        // 신규 → 자동 가입
        );

        return oAuth2User;
    }

    private void createSocialUser(String email, String provider, String socialId) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(null)          // 소셜 전용 계정 — 비밀번호 없음
                .role("USER")
                .status("ACTIVE")
                .emailVerified(true)         // 제공자가 이메일 검증
                .socialId(socialId)
                .socialProvider(provider)
                .build());

        profileRepository.save(Profile.builder()
                .user(user)
                .nickname(generateUniqueNickname(email))
                .isPublic(true)
                .build());

        log.info("[OAuth2] 신규 소셜 계정 생성 — provider={}, email={}", provider, email);
    }

    // 이메일 local part 기반으로 유니크 닉네임 생성 (충돌 시 숫자 접미사)
    private String generateUniqueNickname(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "");
        if (base.isBlank()) base = "user";
        if (base.length() > 20) base = base.substring(0, 20);

        if (!profileRepository.existsByNickname(base)) return base;
        for (int i = 0; i < 10; i++) {
            String candidate = base + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (!profileRepository.existsByNickname(candidate)) return candidate;
        }
        // 극히 드문 연속 충돌 — 타임스탬프로 폴백
        return base + System.currentTimeMillis();
    }
}
