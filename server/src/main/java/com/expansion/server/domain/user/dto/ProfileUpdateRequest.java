package com.expansion.server.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProfileUpdateRequest {

    @Pattern(regexp = "^[a-zA-Z0-9가-힣_]{2,30}$",
            message = "닉네임은 영문자, 숫자, 한글, 언더스코어만 사용 가능하며 2~30자여야 합니다.")
    private String nickname;

    @Size(max = 255, message = "소개는 255자 이하로 입력해주세요.")
    private String bio;

    @Size(max = 255, message = "웹사이트 URL은 255자 이하로 입력해주세요.")
    private String websiteUrl;

    // profileImageUrl은 받지 않음 — 프로필 이미지는 POST/DELETE /api/users/me/profile-image 전용
    // (클라이언트가 임의 URL을 지정하지 못하게, 그리고 텍스트 수정 시 사진이 지워지지 않게)

    private Boolean isPublic;
}
