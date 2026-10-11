-- V39: 챌린지 주제 풀 — AI 자동 보충(C-2b) + 관리자 관리 준비
-- source: SEED(V38 기본) / AI(Gemini 자동 생성) / ADMIN(관리자가 직접 추가)
-- status: ACTIVE(꺼내 쓸 수 있음) / DISABLED(사용 중지 — 스케줄러가 건너뜀, 행은 남겨 중복 방지에 계속 쓰임)
ALTER TABLE challenge_topics
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'SEED',
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT chk_challenge_topics_source CHECK (source IN ('SEED', 'AI', 'ADMIN')),
    ADD CONSTRAINT chk_challenge_topics_status CHECK (status IN ('ACTIVE', 'DISABLED'));

-- 같은 주제 중복 금지 — 띄어쓰기·대소문자 무시('밤하늘' = '밤 하늘'). 서버 정규화와 같은 식이어야 함
CREATE UNIQUE INDEX uq_challenge_topics_topic_norm
    ON challenge_topics (lower(regexp_replace(topic, '\s', '', 'g')));
