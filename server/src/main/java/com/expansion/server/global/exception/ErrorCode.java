package com.expansion.server.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 리소스입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),

    // 이메일 인증
    INVALID_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 인증 링크입니다."),
    EXPIRED_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "만료된 인증 링크입니다. 인증 메일을 다시 요청해 주세요."),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 인증된 이메일입니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증 후 이용할 수 있습니다."),
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다."),
    BANNED_USER(HttpStatus.FORBIDDEN, "정지된 계정입니다."),
    DELETED_USER(HttpStatus.GONE, "탈퇴한 계정입니다."),
    CANNOT_FOLLOW_SELF(HttpStatus.BAD_REQUEST, "자기 자신을 팔로우할 수 없습니다."),
    ALREADY_FOLLOWING(HttpStatus.CONFLICT, "이미 팔로우한 사용자입니다."),

    // 공통 접근
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 갤러리
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 게시물에 접근 권한이 없습니다."),
    NOT_EDITABLE_POST(HttpStatus.FORBIDDEN, "리믹스가 허용되지 않은 게시물입니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."),

    // 에셋
    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 에셋입니다."),
    ALREADY_PURCHASED(HttpStatus.CONFLICT, "이미 구매한 에셋입니다."),
    PURCHASE_REQUIRED(HttpStatus.FORBIDDEN, "에셋 구매 후 이용 가능합니다."),

    // 에디터
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 프로젝트입니다."),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 프로젝트에 접근 권한이 없습니다."),

    // 커미션
    COMMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 커미션입니다."),
    INVALID_COMMISSION_STATUS(HttpStatus.BAD_REQUEST, "유효하지 않은 커미션 상태 전환입니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 지원입니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 지원한 의뢰입니다.");

    private final HttpStatus status;
    private final String message;
}
