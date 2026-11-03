package com.expansion.server.domain.user.service;

import com.expansion.server.domain.user.entity.EmailVerificationToken;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.EmailVerificationTokenRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.expansion.server.global.mail.MailService;
import com.expansion.server.global.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TTL_HOURS = 24;
    static final int RESEND_COOLDOWN_SECONDS = 60;   // 재발송 간격 — 비밀번호 찾기와 같은 규칙
    static final int DAILY_SEND_LIMIT = 5;            // 24시간 발송 상한(가입 메일 포함) — 메일 폭탄·발송 한도 소진 방지

    @Value("${mail.verification-base-url:http://localhost:5173}")
    private String verificationBaseUrl;

    /**
     * 인증 토큰 발급 + 메일 발송. 이미 인증된 사용자는 무시(true).
     * @return 발송 성공 여부(false=발송 실패/미구성 — 호출부가 재시도/에러 처리 가능)
     */
    @Transactional
    public boolean issueAndSend(User user) {
        if (user.isEmailVerified()) return true;

        // 이전 토큰 무효화(이전 링크 무효화) — 항상 최신 링크 1개만 유효. 행은 발송 횟수 집계용으로 남김
        tokenRepository.invalidateUnused(user.getUserId(), LocalDateTime.now());

        // 256-bit 랜덤 원문 토큰 → 링크에 싣고, DB엔 해시만 저장
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = HexFormat.of().formatHex(bytes);

        tokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(HashUtil.sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plusHours(TTL_HOURS))
                .build());

        String link = verificationBaseUrl + "/verify-email?token=" + rawToken;
        return mailService.send(
                user.getEmail(),
                "[PixelPilot] 이메일 인증을 완료해 주세요",
                "PixelPilot 가입을 환영합니다!\n\n"
                        + "아래 링크를 눌러 이메일 인증을 완료해 주세요 (" + TTL_HOURS + "시간 동안 유효):\n"
                        + link + "\n\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.");
    }

    /** 인증 링크의 원문 토큰으로 검증 → 성공 시 user.email_verified = true */
    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_VERIFICATION_TOKEN));

        if (token.isUsed()) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }
        if (token.isExpired()) {
            throw new CustomException(ErrorCode.EXPIRED_VERIFICATION_TOKEN);
        }

        token.markUsed();
        token.getUser().verifyEmail();
    }

    /** 로그인 유저가 인증 메일 재발송 요청 — 발송 실패 시 에러로 사용자에게 전달 */
    @Transactional
    public void resend(Long userId) {
        // 행 잠금 — 동시에 여러 번 눌러도 횟수 판정이 한 번씩 차례로(둘 다 통과해 2통 가는 것 방지)
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.isEmailVerified()) {
            throw new CustomException(ErrorCode.ALREADY_VERIFIED);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean coolingDown = tokenRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(userId)
                .map(t -> t.getCreatedAt().isAfter(now.minusSeconds(RESEND_COOLDOWN_SECONDS)))
                .orElse(false);
        if (coolingDown) {
            throw new CustomException(ErrorCode.VERIFICATION_RESEND_COOLDOWN);
        }
        if (tokenRepository.countByUser_UserIdAndCreatedAtAfter(userId, now.minusHours(24)) >= DAILY_SEND_LIMIT) {
            throw new CustomException(ErrorCode.VERIFICATION_RESEND_LIMIT);
        }
        if (!issueAndSend(user)) {
            throw new CustomException(ErrorCode.MAIL_SEND_FAILED);
        }
    }
}
