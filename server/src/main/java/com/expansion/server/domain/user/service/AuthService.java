package com.expansion.server.domain.user.service;

import com.expansion.server.domain.user.dto.LoginRequest;
import com.expansion.server.domain.user.dto.SignupRequest;
import com.expansion.server.domain.user.dto.TokenRefreshRequest;
import com.expansion.server.domain.user.dto.TokenResponse;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.RefreshToken;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.event.UserRegisteredEvent;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.RefreshTokenRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.expansion.server.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher eventPublisher;

    // ── 회원가입 ───────────────────────────────────────────
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (profileRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = userRepository.save(User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .status("ACTIVE")
                .emailVerified(false)
                .build());

        profileRepository.save(Profile.builder()
                .user(user)
                .nickname(request.getNickname())
                .isPublic(true)
                .build());

        // 인증 메일은 가입 트랜잭션 '커밋 이후'에 발송(롤백 시 무효 링크 방지) — 이벤트로 분리
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getUserId()));

        return issueTokens(user);
    }

    // ── 로그인 ─────────────────────────────────────────────
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        // 없는 이메일·소셜 전용(비밀번호 없음)·비밀번호 틀림을 같은 응답(LOGIN_FAILED)으로 — 가입 여부 노출 방지.
        // 없는 경우에도 더미 해시와 비교해 BCrypt 비용을 똑같이 써서 응답 시간 차이도 없앤다.
        String hash = (user != null && user.hasPassword()) ? user.getPasswordHash() : dummyHash();
        boolean matches = passwordEncoder.matches(request.getPassword(), hash);
        if (user == null || !user.hasPassword() || !matches) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }
        validateActiveStatus(user);

        user.updateLastLogin();
        return issueTokens(user);
    }

    // 계정 상태 검증 (탈퇴/정지) — login·OAuth 공용
    private void validateActiveStatus(User user) {
        if ("DELETED".equals(user.getStatus())) {
            throw new CustomException(ErrorCode.DELETED_USER);
        }
        if ("BANNED".equals(user.getStatus())) {
            throw new CustomException(ErrorCode.BANNED_USER);
        }
    }

    // ── 소셜 로그인 토큰 발급 ───────────────────────────────
    // OAuth2 성공 핸들러용. 이메일로 유저 조회 후 access/refresh 토큰 발급(refresh는 DB 저장).
    public TokenResponse issueTokensForOAuth(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        validateActiveStatus(user);
        user.updateLastLogin();
        return issueTokens(user);
    }

    // ── 토큰 재발급 ────────────────────────────────────────
    public TokenResponse refresh(TokenRefreshRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshToken saved = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!saved.isValid()) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        saved.revoke();   // 기존 토큰 무효화 (rotation)
        return issueTokens(saved.getUser());
    }

    // ── 로그아웃 ───────────────────────────────────────────
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    /** 비밀번호 변경 후 현재 기기용 토큰 재발급(PasswordService) */
    public TokenResponse issueTokensFor(User user) {
        return issueTokens(user);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────
    private volatile String dummyHash;

    /** 로그인 타이밍 균일화용 더미 BCrypt 해시(최초 1회 생성) */
    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = passwordEncoder.encode("timing-equalizer-" + System.nanoTime());
            dummyHash = h;
        }
        return h;
    }

    private TokenResponse issueTokens(User user) {
        String accessToken  = jwtUtil.generateAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessTokenExpirySeconds())   // 설정값에서 파생(하드코딩 제거)
                .build();
    }

    /** refresh token 원문을 SHA-256 해시하여 DB에 저장 */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
