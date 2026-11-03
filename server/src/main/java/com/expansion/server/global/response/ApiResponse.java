package com.expansion.server.global.response;

import com.expansion.server.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 응답 봉투. 실패 응답의 code는 ErrorCode 이름(예: EMAIL_ALREADY_EXISTS) —
 * 프론트는 문구가 아니라 이 값으로 분기한다(문구는 바뀔 수 있음). 성공 응답은 code 생략.
 * 실패는 반드시 ErrorCode로 만든다(문구만 받는 fail(String)은 없앰 — 코드 없는 실패가 생기지 않게).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, null, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, null, message, data);
    }

    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.getMessage(), null);
    }

    /** 코드는 그대로, 문구만 상황에 맞게(예: 검증 오류의 필드별 문구) */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.name(), message, null);
    }

    // ok() 별칭 — 컨트롤러에서 success() 사용 시
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, null, data);
    }
}
