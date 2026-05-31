-- V16: 작가 서비스에 카테고리(분야) 컬럼 추가
-- 허브에서 분야별 필터링을 위해 단일 카테고리 보관 (캐릭터/배경·환경/애니메이션/게임 에셋/초상화/기타)
-- 기존 행은 '기타'로 채운다.

ALTER TABLE commission_services
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT '기타';

CREATE INDEX idx_commission_services_category ON commission_services (category);
