package com.expansion.server.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BlockResponse {

    /** 차단된 사용자 목록 (닉네임/프로필 이미지 포함) */
    private List<BlockedUserInfo> blockedUsers;

    /** 차단된 태그 목록 */
    private List<String> blockedTags;
}
