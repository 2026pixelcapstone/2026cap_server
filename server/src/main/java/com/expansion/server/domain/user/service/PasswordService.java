package com.expansion.server.domain.user.service;

import com.expansion.server.domain.user.dto.TokenResponse;
import com.expansion.server.domain.user.entity.PasswordResetToken;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.PasswordResetTokenRepository;
import com.expansion.server.domain.user.repository.RefreshTokenRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.expansion.server.global.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 비밀번호 찾기(재설정 메일)·재설정·변경.
 * - 찾기: 가입 여부와 무관하게 항상 같은 응답(호출부), 메일은 커밋 후 비동기 발송 → 응답 시간도 같음
 * - 재설정·변경 성공 시 모든 refresh 토큰 폐기(다른 기기 로그아웃). 변경은 현재 기기에 새 토큰 발급
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordService {

    static final int RESET_TTL_MINUTES = 30;
    static final int RESET_COOLDOWN_SECONDS = 60;   // 같은 사용자 재요청 간격(메일 폭탄 방지)
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer resetMailer;
    private final AuthService authService;

    @Value("${mail.verification-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    // ── 비밀번호 찾기 ────────────────────────────────────────
    /**
     * 재설정 메일 요청. 계정 없음·소셜 전용·탈퇴/정지·쿨다운 중이면 조용히 아무것도 하지 않는다
     * (어떤 경우든 호출부는 같은 200 응답 → 가입 여부 노출 없음).
     */
    public void requestReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.hasPassword() || !"ACTIVE".equals(user.getStatus())) return;

        boolean coolingDown = resetTokenRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(user.getUserId())
                .map(t -> t.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RESET_COOLDOWN_SECONDS)))
                .orElse(false);
        if (coolingDown) return;

        // 이전 링크 무효화 — 항상 최신 링크 1개만 유효
        resetTokenRepository.deleteAllByUserId(user.getUserId());

        byte[] bytes = new byte[32];   // 256-bit 원문 → 링크에만, DB엔 해시
        RANDOM.nextBytes(bytes);
        String rawToken = HexFormat.of().formatHex(bytes);
        resetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(HashUtil.sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(RESET_TTL_MINUTES))
                .build());

        String to = user.getEmail();
        String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
        // 커밋된 뒤에만 발송(롤백 시 무효 링크 방지) + 비동기(응답 시간 동일화)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    resetMailer.send(to, link, RESET_TTL_MINUTES);
                } catch (TaskRejectedException e) {
                    log.warn("[MAIL] 발송 대기열 가득 참 — 비밀번호 재설정 메일 생략");
                }
            }
        });
    }

    // ── 재설정(링크) ─────────────────────────────────────────
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_RESET_TOKEN));
        if (token.isUsed()) throw new CustomException(ErrorCode.INVALID_RESET_TOKEN);
        if (token.isExpired()) throw new CustomException(ErrorCode.EXPIRED_RESET_TOKEN);

        // 원자적 사용 처리 — 동시에 같은 링크로 두 번 와도 한쪽만 통과
        if (resetTokenRepository.markUsedIfValid(token.getTokenId(), LocalDateTime.now()) == 0) {
            throw new CustomException(ErrorCode.INVALID_RESET_TOKEN);
        }

        User user = token.getUser();
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_RESET_TOKEN);   // 발급 후 탈퇴·정지된 계정
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        if (!user.isEmailVerified()) user.verifyEmail();   // 메일 링크를 열었다 = 이메일 소유 증명

        resetTokenRepository.deleteAllByUserId(user.getUserId());   // 남은 링크 정리
        refreshTokenRepository.revokeAllByUserId(user.getUserId()); // 모든 기기 로그아웃
    }

    // ── 변경(로그인 상태) ────────────────────────────────────
    /** 현재 비밀번호 확인 후 변경 → 모든 refresh 폐기 + 현재 기기용 새 토큰 발급 */
    public TokenResponse changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.hasPassword()) {
            throw new CustomException(ErrorCode.PASSWORD_NOT_SET);   // 소셜 전용 계정
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.SAME_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByUserId(userId);   // 다른 기기 로그아웃
        return authService.issueTokensFor(user);              // 현재 기기는 유지
    }
}
