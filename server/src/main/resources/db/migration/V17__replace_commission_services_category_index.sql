-- V17: 작가 서비스 공개 목록 조회는 항상 status='OPEN' + (선택)category로 필터링한다.
-- 단일 컬럼 인덱스(category)보다 (status, category) 복합 인덱스가 접근 패턴에 맞고 선택도가 높다.
-- V16은 이미 적용됐을 수 있으므로 직접 수정하지 않고 후속 마이그레이션으로 인덱스를 교체한다.

DROP INDEX IF EXISTS idx_commission_services_category;

CREATE INDEX idx_commission_services_status_category
    ON commission_services (status, category);
