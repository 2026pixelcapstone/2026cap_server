-- V22: 에셋 카테고리/라이선스 활성화
--  ① categories의 전역 UNIQUE(name) → (name, type) 복합 UNIQUE (타입별 동일 이름 허용: 갤러리 '캐릭터' ≠ 에셋 '캐릭터')
--  ② 에셋 카테고리 시드 보강
--  ③ 에셋 라이선스 종류 시드(현재 0개라 선택지가 없었음)

-- ① 기존 name UNIQUE 제약 제거(이름이 환경마다 다를 수 있어 동적 탐색) 후 (name, type) 복합 UNIQUE 추가
DO $$
DECLARE cname text;
BEGIN
    SELECT con.conname INTO cname
    FROM pg_constraint con
    WHERE con.conrelid = 'categories'::regclass
      AND con.contype = 'u'
      AND con.conkey = ARRAY[(SELECT attnum FROM pg_attribute
                              WHERE attrelid = 'categories'::regclass AND attname = 'name')];
    IF cname IS NOT NULL THEN
        EXECUTE 'ALTER TABLE categories DROP CONSTRAINT ' || quote_ident(cname);
    END IF;
END $$;

ALTER TABLE categories ADD CONSTRAINT categories_name_type_key UNIQUE (name, type);

-- ② 에셋 카테고리 시드 (이미 있으면 무시)
INSERT INTO categories (name, type, sort_order) VALUES
    ('캐릭터', 'ASSET', 1),
    ('타일셋', 'ASSET', 2),
    ('배경',   'ASSET', 3),
    ('아이콘', 'ASSET', 4),
    ('UI',     'ASSET', 5),
    ('이펙트', 'ASSET', 6),
    ('폰트',   'ASSET', 7)
ON CONFLICT (name, type) DO NOTHING;

-- ③ 에셋 라이선스 종류 시드
INSERT INTO asset_license_types (name, can_commercial, can_modify, can_redistribute, require_attribution, is_exclusive, description) VALUES
    ('CC0 (퍼블릭 도메인)', TRUE,  TRUE, TRUE,  FALSE, FALSE, '출처 표시 없이 사용/수정/재배포 자유.'),
    ('CC-BY (출처 표시)',   TRUE,  TRUE, TRUE,  TRUE,  FALSE, '제작자(출처) 표시 시 자유롭게 사용 가능.'),
    ('개인 사용만',         FALSE, TRUE, FALSE, FALSE, FALSE, '비상업적 개인 용도로만 사용 가능.'),
    ('상업적 이용 가능',    TRUE,  TRUE, FALSE, FALSE, FALSE, '상업적 프로젝트에 사용 가능. 재배포 불가.'),
    ('독점 (구매자 전용)',  TRUE,  TRUE, FALSE, FALSE, TRUE,  '구매자에게 독점 제공. 재배포 불가.');
