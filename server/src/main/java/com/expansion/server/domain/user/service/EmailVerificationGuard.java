package com.expansion.server.domain.user.service;

import com.expansion.server.domain.user.entity.User;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;

/**
 * 소프트 게이트 정책의 단일 지점.
 * 콘텐츠 생성(갤러리 글·에셋 업로드·에디터 프로젝트·커미션 등록) 진입부에서
 * 이미 로드된 User로 호출 → 미인증이면 EMAIL_NOT_VERIFIED.
 * 정적 메서드라 추가 DB 조회 없이, 예외 타입/메시지/조건을 한 곳에서 관리.
 */
public final class EmailVerificationGuard {

    private EmailVerificationGuard() {}

    public static void assertVerified(User user) {
        if (!user.isEmailVerified()) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }
}
