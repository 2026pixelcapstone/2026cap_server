package com.expansion.server.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlockedUserInfo {

    private Long userId;
    private String nickname;
    private String profileImageUrl;
}
