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
    private LocalDateTime createdAt;

    public static UserProfileResponse of(User user, Profile profile, boolean isFollowing) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
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
}
