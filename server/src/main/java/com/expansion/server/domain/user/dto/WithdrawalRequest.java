package com.expansion.server.domain.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원 탈퇴 본인 확인 — 비밀번호 계정은 password, 소셜 전용 계정은 confirmNickname(현재 닉네임 그대로) */
@Getter
@NoArgsConstructor
public class WithdrawalRequest {

    @Size(max = 100)
    private String password;

    @Size(max = 50)
    private String confirmNickname;
}
