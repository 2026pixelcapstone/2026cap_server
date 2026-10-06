package com.expansion.server.domain.user.dto;

import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileResponse {

    private Long userId;
    /** 로그인 이메일 — 개인정보라 내 정보(ofMe)에만 채움. 타인 조회(공개 프로필·팔로워 목록 등)는 null */
    private String email;
    private String nickname;
    private String bio;
    private String profileImageUrl;
    private String websiteUrl;
    private int followerCount;
    private int followingCount;
    private boolean isPublic;
    private boolean isFollowing;   // 현재 로그인 유저가 이 유저를 팔로우 중인지
    private boolean emailVerified; // 이메일 인증 여부 (소프트 게이트 — 프론트 배너/버튼 제어용)
    private String role;
    /** 비밀번호 로그인 가능 여부(소셜 전용이면 false) — 내 정보(ofMe)에만 채우고 타인 조회는 null */
    private Boolean hasPassword;
    private LocalDateTime createdAt;

    /** 타인에게 보여줄 프로필 — email·hasPassword 없음(비로그인 공개 API에서도 쓰임) */
    public static UserProfileResponse of(User user, Profile profile, boolean isFollowing) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .emailVerified(user.isEmailVerified())
                .nickname(profile.getNickname())
                .bio(profile.getBio())
                .profileImageUrl(profile.getProfileImageUrl())
                .websiteUrl(profile.getWebsiteUrl())
                .followerCount(profile.getFollowerCount())
                .followingCount(profile.getFollowingCount())
                .isPublic(profile.isPublic())
                .isFollowing(isFollowing)
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /** 내 정보 응답 — 본인만 보는 필드(email·hasPassword) 포함 */
    public static UserProfileResponse ofMe(User user, Profile profile) {
        UserProfileResponse r = of(user, profile, false);
        r.email = user.getEmail();
        r.hasPassword = user.hasPassword();
        return r;
    }
}
