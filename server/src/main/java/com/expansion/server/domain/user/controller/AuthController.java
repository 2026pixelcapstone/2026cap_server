package com.expansion.server.domain.user.controller;

import com.expansion.server.domain.user.dto.EmailVerifyRequest;
import com.expansion.server.domain.user.dto.LoginRequest;
import com.expansion.server.domain.user.dto.PasswordForgotRequest;
import com.expansion.server.domain.user.dto.PasswordResetRequest;
import com.expansion.server.domain.user.dto.SignupRequest;
import com.expansion.server.domain.user.dto.TokenRefreshRequest;
import com.expansion.server.domain.user.dto.TokenResponse;
import com.expansion.server.domain.user.service.AuthService;
import com.expansion.server.domain.user.service.EmailVerificationService;
import com.expansion.server.domain.user.service.PasswordService;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.expansion.server.global.response.ApiResponse;
import com.expansion.server.global.security.abuseipdb.LoginAbuseReporter;
import com.expansion.server.global.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordService passwordService;
    private final ClientIpResolver clientIpResolver;
    private final LoginAbuseReporter loginAbuseReporter;

    /**
     * POST /api/auth/signup
     * 이메일 회원가입 → access/refresh 토큰 즉시 발급
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        TokenResponse tokens = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("회원가입이 완료되었습니다.", tokens));
    }

    /**
     * POST /api/auth/login
     * 이메일/비밀번호 로그인 → access/refresh 토큰 발급
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        try {
            TokenResponse tokens = authService.login(request);
            loginAbuseReporter.recordSuccess(ip);   // 성공 → 실패 누적 초기화
            return ResponseEntity.ok(ApiResponse.ok(tokens));
        } catch (CustomException e) {
            // 로그인 실패(없는 이메일·비번 틀림 통합) → brute-force 신호로 카운트
            if (e.getErrorCode() == ErrorCode.LOGIN_FAILED) {
                loginAbuseReporter.recordFailure(ip);
            }
            throw e;
        }
    }

    /**
     * POST /api/auth/refresh
     * refresh 토큰으로 access 토큰 재발급 (Token Rotation)
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request) {
        TokenResponse tokens = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.ok(tokens));
    }

    /**
     * POST /api/auth/logout
     * 해당 유저의 모든 refresh 토큰 무효화
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 되었습니다."));
    }

    /**
     * POST /api/auth/email/verify
     * 인증 메일 링크의 토큰으로 이메일 인증 완료 (비로그인 상태에서도 호출 가능)
     */
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody EmailVerifyRequest request) {
        emailVerificationService.verify(request.getToken());
        return ResponseEntity.ok(ApiResponse.ok("이메일 인증이 완료되었습니다."));
    }

    /**
     * POST /api/auth/email/resend
     * 인증 메일 재발송 (로그인 필수)
     */
    @PostMapping("/email/resend")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail("인증이 필요합니다."));
        }
        emailVerificationService.resend(userId);
        return ResponseEntity.ok(ApiResponse.ok("인증 메일을 다시 보냈습니다."));
    }

    /**
     * POST /api/auth/password/forgot
     * 비밀번호 재설정 메일 요청(비로그인). 가입 여부와 무관하게 항상 같은 응답 — 이메일 존재 노출 방지.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody PasswordForgotRequest request) {
        passwordService.requestReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("가입된 이메일이라면 비밀번호 재설정 링크를 보냈습니다."));
    }

    /**
     * POST /api/auth/password/reset
     * 메일 링크의 토큰으로 새 비밀번호 설정(비로그인). 성공 시 모든 기기 로그아웃.
     */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {
        passwordService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요."));
    }
}
