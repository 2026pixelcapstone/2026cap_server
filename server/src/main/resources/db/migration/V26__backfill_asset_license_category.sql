-- V26: 기존 에셋의 누락된 라이선스/카테고리 백필
-- 기능을 순차 도입하면서(V22 라이선스/카테고리 시드) 그 전에 만들어졌거나 선택 안 한 에셋들이
-- license_type_id / category_id 가 NULL로 남아있음 → 기존 행만(IS NULL) 기본값으로 채움(멱등).
-- 환경마다 id가 다를 수 있어 이름으로 참조.

-- ① 라이선스 — 무료(price 0/null) → CC0, 나머지(유료) → 상업적 이용 가능
UPDATE assets
SET license_type_id = (SELECT license_type_id FROM asset_license_types WHERE name = 'CC0 (퍼블릭 도메인)')
WHERE license_type_id IS NULL
  AND COALESCE(price, 0) = 0;

UPDATE assets
SET license_type_id = (SELECT license_type_id FROM asset_license_types WHERE name = '상업적 이용 가능')
WHERE license_type_id IS NULL;

-- ② 카테고리 — 누락분은 에셋 카테고리(V22 시드 7종) 중에서 행마다 랜덤 배정.
--    a.asset_id 참조로 상관 서브쿼리화 → 행마다 random()이 재평가되어 카테고리가 분산됨.
UPDATE assets a
SET category_id = (
    SELECT c.category_id
    FROM categories c
    WHERE c.type = 'ASSET'
      AND c.name IN ('캐릭터', '타일셋', '배경', '아이콘', 'UI', '이펙트', '폰트')
      AND a.asset_id IS NOT NULL
    ORDER BY random()
    LIMIT 1
)
WHERE a.category_id IS NULL;
