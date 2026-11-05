-- V41: 공동 픽셀 캔버스(커뮤니티 C-3 본체)
-- 판 64×64, 칸마다 색 번호 0~15(1바이트) → board BYTEA 4096. 0 = 흰색(빈 칸). 팔레트 16색은 서버 코드(SharedCanvasPalette)가 정함.
-- 시즌 = 분기(1·4·7·10월 1일 00:00 KST 시작). 스케줄러(+서버 기동 시 보충)가 지난 시즌을 닫고 새 시즌을 연다.
-- 쿨다운 판정은 canvas_cooldowns 한 줄 원자적 갱신(서버가 판정), 찍기 기록은 관리자 되돌리기·재계산용.

CREATE TABLE canvas_seasons (
    season_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    starts_at  TIMESTAMP NOT NULL,
    ends_at    TIMESTAMP NOT NULL,
    board      BYTEA     NOT NULL,
    version    BIGINT    NOT NULL DEFAULT 0,
    closed_at  TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_canvas_seasons_starts_at UNIQUE (starts_at),
    CONSTRAINT chk_canvas_seasons_period CHECK (ends_at > starts_at),
    CONSTRAINT chk_canvas_seasons_board CHECK (octet_length(board) = 4096)
);

-- 열린 시즌은 하나만
CREATE UNIQUE INDEX uq_canvas_seasons_open ON canvas_seasons ((closed_at IS NULL)) WHERE closed_at IS NULL;

-- 사용자마다 마지막으로 찍은 시각(시즌과 무관 — 시즌이 바뀌어도 쿨다운 유지)
CREATE TABLE canvas_cooldowns (
    user_id        BIGINT    PRIMARY KEY REFERENCES users (user_id) ON DELETE CASCADE,
    last_placed_at TIMESTAMP NOT NULL
);

-- 찍기 기록. reverted = 관리자 되돌리기로 판에서 뺀 것(행은 남김)
CREATE TABLE canvas_placements (
    placement_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    season_id    BIGINT    NOT NULL REFERENCES canvas_seasons (season_id) ON DELETE CASCADE,
    user_id      BIGINT    NOT NULL REFERENCES users (user_id),
    x            SMALLINT  NOT NULL,
    y            SMALLINT  NOT NULL,
    color        SMALLINT  NOT NULL,
    version      BIGINT    NOT NULL,
    reverted     BOOLEAN   NOT NULL DEFAULT FALSE,
    placed_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_canvas_placements_xy CHECK (x BETWEEN 0 AND 63 AND y BETWEEN 0 AND 63),
    CONSTRAINT chk_canvas_placements_color CHECK (color BETWEEN 0 AND 15)
);

CREATE INDEX idx_canvas_placements_season_time ON canvas_placements (season_id, placed_at);
CREATE INDEX idx_canvas_placements_season_user ON canvas_placements (season_id, user_id);
