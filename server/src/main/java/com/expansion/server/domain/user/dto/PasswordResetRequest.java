package com.expansion.server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasswordResetRequest {

    @NotBlank(message = "재설정 토큰이 필요합니다.")
    @Size(max = 128, message = "재설정 토큰이 올바르지 않습니다.")
    private String token;

    // 가입 규칙(SignupRequest)과 동일
    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하로 입력해주세요.")
    private String newPassword;
}
