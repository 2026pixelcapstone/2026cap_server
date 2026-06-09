-- V19: 이메일 인증 토큰 테이블
-- 회원가입/재발송 시 발급. token_hash = 원문 토큰의 SHA-256 hex(64자), refresh_tokens와 동일 패턴.
-- 링크에는 원문 토큰을 싣고, 검증은 해시로 조회.

CREATE TABLE email_verification_tokens (
    token_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(user_id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verification_user ON email_verification_tokens(user_id);
