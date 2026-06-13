package com.expansion.server.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알림 종류. titleTemplate의 %s에 행동 주체(sender) 닉네임이 채워진다.
 * targetType은 프론트 클릭 시 이동 경로 분기 키(GALLERY/ASSET/USER/COMMISSION/REQUEST_POST).
 * 채팅 메시지는 알림 row를 만들지 않고 안읽음 집계 카운트로만 노출하므로 여기에 없음.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    GALLERY_COMMENT("%s님이 회원님의 작품에 댓글을 남겼습니다.", "GALLERY"),
    ASSET_COMMENT("%s님이 회원님의 에셋에 댓글을 남겼습니다.", "ASSET"),
    FOLLOW("%s님이 회원님을 팔로우하기 시작했습니다.", "USER"),

    COMMISSION_APPLY("%s님이 회원님의 의뢰에 지원했습니다.", "REQUEST_POST"),
    COMMISSION_ACCEPT("%s님이 회원님의 지원을 수락했습니다.", "COMMISSION"),
    COMMISSION_REVIEW("%s님이 작업물 검토를 요청했습니다.", "COMMISSION"),
    COMMISSION_COMPLETED("%s님이 커미션을 완료 확정했습니다.", "COMMISSION"),
    COMMISSION_CANCELLED("%s님이 커미션을 취소했습니다.", "COMMISSION");

    private final String titleTemplate;
    private final String targetType;
}
