-- V38: 주간 챌린지(커뮤니티 C-2 본체)
-- 한 주 = 월 00:00 KST ~ 다음 월 00:00 KST(ends_at은 미포함 경계). 시각은 다른 테이블과 같은 앱 LocalDateTime 기준.
-- 매주 월 0시 스케줄러(+서버 기동 시 보충)가 지난 챌린지 판정 → 이번 주 챌린지 생성을 한다.

-- 주제 풀: used_at 없는 것부터 sort_order 순으로 꺼내 쓰고, 다 쓰면 가장 오래전에 쓴 것부터 재사용.
-- (C-2b에서 AI가 이 풀을 채운다)
CREATE TABLE challenge_topics (
    topic_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic       VARCHAR(50)   NOT NULL,
    description VARCHAR(300),
    sort_order  INT           NOT NULL DEFAULT 0,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 챌린지: 주제 문구는 풀에서 복사해 둔다(풀을 고쳐도 지난 기록 유지).
-- winner_count = 판정 때 순위를 매긴 수(0~3). 우승작이 나중에 삭제돼도 '빈 자리'로 표시하기 위함.
CREATE TABLE challenges (
    challenge_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic        VARCHAR(50)   NOT NULL,
    description  VARCHAR(300),
    starts_at    TIMESTAMP     NOT NULL,
    ends_at      TIMESTAMP     NOT NULL,
    judged_at    TIMESTAMP,
    winner_count SMALLINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_challenges_starts_at UNIQUE (starts_at),
    CONSTRAINT chk_challenges_period CHECK (ends_at > starts_at)
);

-- 참가: 1인 1작(교체 시 post_id만 바뀜). 작품이 삭제되면 참가도 함께 삭제.
-- final_rank/final_like_count는 판정 때 톱3만 채운다.
CREATE TABLE challenge_entries (
    entry_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    challenge_id     BIGINT      NOT NULL REFERENCES challenges(challenge_id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL REFERENCES users(user_id),
    post_id          BIGINT      NOT NULL REFERENCES gallery_posts(post_id) ON DELETE CASCADE,
    final_rank       SMALLINT,
    final_like_count INT,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_challenge_entries_user UNIQUE (challenge_id, user_id),
    CONSTRAINT chk_challenge_entries_rank CHECK (final_rank IS NULL OR final_rank BETWEEN 1 AND 3)
);

CREATE INDEX idx_challenge_entries_post ON challenge_entries(post_id);

-- 기본 주제 12개
INSERT INTO challenge_topics (topic, description, sort_order) VALUES
('작은 생물',   '손바닥보다 작은 생물을 그려 보세요.', 1),
('비 오는 날',  '비가 내리는 장면을 그려 보세요.', 2),
('나의 방',     '내 방이나 머물고 싶은 방을 그려 보세요.', 3),
('던전 입구',   '모험이 시작되는 입구를 그려 보세요.', 4),
('간식 시간',   '좋아하는 간식을 그려 보세요.', 5),
('바닷속',      '물속 세상을 그려 보세요.', 6),
('로봇 친구',   '곁에 두고 싶은 로봇을 그려 보세요.', 7),
('마법 아이템', '신비한 힘이 깃든 물건을 그려 보세요.', 8),
('밤하늘',      '밤하늘이 보이는 장면을 그려 보세요.', 9),
('탈것',        '무언가를 타고 가는 모습을 그려 보세요.', 10),
('숲의 정령',   '숲에 사는 신비한 존재를 그려 보세요.', 11),
('시장 골목',   '북적이는 시장 한쪽을 그려 보세요.', 12);
