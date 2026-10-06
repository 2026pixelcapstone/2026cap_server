package com.expansion.server.domain.user.dto;

import com.expansion.server.domain.user.entity.Profile;

/** 메인 '인기 작가' — 최근 기간에 받은 좋아요 수(recentLikes, 채움 항목은 0) */
public record PopularUserResponse(Long userId, String nickname, String profileImageUrl,
                                  int followerCount, long recentLikes) {

    public static PopularUserResponse of(Profile p, long recentLikes) {
        return new PopularUserResponse(p.getUser().getUserId(), p.getNickname(), p.getProfileImageUrl(),
                p.getFollowerCount(), recentLikes);
    }
}
