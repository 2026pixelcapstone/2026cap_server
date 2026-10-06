-- V36: 팔레트 공유(커뮤니티 C-1, Lospec형)
-- colors = '#rrggbb' 소문자 JSON 배열(서버가 정규화·중복 제거), color_count = 그 길이(필터·정렬용).
-- user_id NULL = 사이트 기본 제공(공식) 팔레트 — 작성자 계정 없이 모든 환경에 동일하게 들어가고 누구도 수정·삭제 불가.
-- 좋아요는 공용 likes 테이블(target_type='PALETTE') 사용, like_count는 비정규화 카운터.

CREATE TABLE palettes (
    palette_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT        REFERENCES users(user_id),
    name         VARCHAR(50)   NOT NULL,
    description  VARCHAR(500),
    colors       JSONB         NOT NULL,
    color_count  INT           NOT NULL,
    like_count   INT           NOT NULL DEFAULT 0,
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_palettes_color_count CHECK (color_count BETWEEN 2 AND 256)
);

CREATE INDEX idx_palettes_user ON palettes(user_id);
CREATE INDEX idx_palettes_status_created ON palettes(status, created_at DESC);
CREATE INDEX idx_palettes_status_likes ON palettes(status, like_count DESC);

-- 기본 제공 팔레트(자체 구성, 기존 유명 팔레트 복제 아님)
INSERT INTO palettes (user_id, name, description, colors, color_count) VALUES
(NULL, '노을빛', '해 질 녘 하늘의 보라에서 금빛까지 이어지는 따뜻한 8색',
 '["#2b1b3d","#4e2a5a","#8a3b6a","#c95a6b","#f08a5d","#f9b872","#ffe3a3","#fff6dc"]', 8),
(NULL, '깊은 숲', '그늘진 숲 바닥부터 햇빛 받은 잎까지, 흙색 하나를 곁들인 8색',
 '["#10221b","#1d3b2a","#2f5a35","#4d7a3a","#7aa04a","#b3c96b","#e3e8a8","#5a3e2b"]', 8),
(NULL, '바다', '심해의 남색에서 모래사장까지 이어지는 8색',
 '["#0b1a33","#123a63","#1b5e8c","#2a8bb8","#4fb8d6","#8fdde8","#d4f4f2","#f2e6c9"]', 8),
(NULL, '회색조 8단', '명암 연습과 흑백 작업용 균등 8단계 회색',
 '["#000000","#242424","#494949","#6d6d6d","#929292","#b6b6b6","#dbdbdb","#ffffff"]', 8),
(NULL, '파스텔', '부드럽고 밝은 8색 파스텔',
 '["#f7c8d0","#f9dcc4","#faf3c0","#cdeac0","#b8e0d2","#b5d3f0","#cdb4ec","#f1e3f3"]', 8),
(NULL, '네온 밤', '어두운 배경 위에 빛나는 네온 강조색 8색',
 '["#0d0b1e","#2a1b4a","#ff2e88","#ff8a00","#f8f32b","#2bf0ff","#7c4dff","#e8e8ff"]', 8),
(NULL, '레트로 휴대용', '옛 휴대용 게임기 화면을 떠올리게 하는 녹색 4단계',
 '["#1f2f1f","#3e5c34","#7d9c4a","#c4d68a"]', 4),
(NULL, '사막', '모래와 바위, 한낮의 열기를 담은 6색',
 '["#3d2b1f","#7a4e2d","#b97a46","#e0a96d","#f3d3a1","#fbf0d9"]', 6);
