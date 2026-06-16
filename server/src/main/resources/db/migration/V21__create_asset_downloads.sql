-- V21: 에셋 다운로드 기록 (사람×에셋 단위) — 다운로드 수를 "받은 사람 수(중복 제거)"로 집계하기 위함
-- 무료/유료 모두 로그인 사용자만 다운로드 가능. 같은 사용자가 다시 받아도 카운트는 1회.

-- 에셋/회원 삭제 시 다운로드 이력이 삭제를 막지 않도록 ON DELETE CASCADE
CREATE TABLE asset_downloads (
    download_id BIGINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT    NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    asset_id    BIGINT    NOT NULL REFERENCES assets(asset_id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, asset_id)
);

CREATE INDEX idx_asset_downloads_asset ON asset_downloads(asset_id);
