-- V35: 비밀번호 재설정 토큰 테이블
-- '비밀번호 찾기' 요청 시 발급. V19(email_verification_tokens)와 같은 패턴:
-- 링크에는 원문 토큰, DB에는 SHA-256 hex(64자)만 저장. 30분 유효·1회용(used_at).

CREATE TABLE password_reset_tokens (
    token_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(user_id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id);
