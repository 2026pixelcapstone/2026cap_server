-- V37: 메인페이지 '이번 주 인기'·'인기 작가' 집계용 — likes를 종류(target_type)+기간(created_at)으로 거른다.
-- 기존 인덱스는 UNIQUE(user_id, target_id, target_type)뿐이라 기간 조건에 쓰이지 않음.
CREATE INDEX idx_likes_type_created ON likes(target_type, created_at);
