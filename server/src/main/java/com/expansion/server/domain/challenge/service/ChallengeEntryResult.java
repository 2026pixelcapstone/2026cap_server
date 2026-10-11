package com.expansion.server.domain.challenge.service;

import com.expansion.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 참가 시도 결과. 작품 등록 중 참가는 예외 대신 이 값으로 돌려준다
 * (참가 실패가 작품 등록을 롤백시키지 않도록 — 작품은 올라가고 참가만 빠짐).
 */
@Getter
@RequiredArgsConstructor
public enum ChallengeEntryResult {

    ENTERED(null),
    REPLACED(null),
    NO_CHALLENGE(ErrorCode.CHALLENGE_NOT_OPEN),
    NOT_OWNER(ErrorCode.ACCESS_DENIED),
    OUT_OF_PERIOD(ErrorCode.CHALLENGE_ENTRY_OUT_OF_PERIOD),
    NOT_PUBLIC(ErrorCode.CHALLENGE_ENTRY_NOT_PUBLIC),
    REMIX(ErrorCode.CHALLENGE_ENTRY_REMIX);

    /** 실패 사유(성공이면 null) */
    private final ErrorCode error;

    public boolean isSuccess() {
        return error == null;
    }
}
