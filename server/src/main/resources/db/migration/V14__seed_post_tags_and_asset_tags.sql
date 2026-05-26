-- V14: 시드 게시물 및 에셋에 태그 연결 데이터 추가

-- ── 갤러리 게시물 태그 연결 ────────────────────────────────

-- 판타지 기사 스프라이트 → 판타지, RPG, 캐릭터, 픽셀아트
INSERT INTO post_tags (post_id, tag_id)
SELECT p.post_id, t.tag_id
FROM gallery_posts p, tags t
WHERE p.title = '판타지 기사 스프라이트'
  AND t.tag_name IN ('판타지', 'RPG', '캐릭터', '픽셀아트');

-- 사이버펑크 시티 배경 → 사이버펑크, 배경, 픽셀아트
INSERT INTO post_tags (post_id, tag_id)
SELECT p.post_id, t.tag_id
FROM gallery_posts p, tags t
WHERE p.title = '사이버펑크 시티 배경'
  AND t.tag_name IN ('사이버펑크', '배경', '픽셀아트');

-- 폭발 이펙트 애니메이션 → 애니메이션, 픽셀아트
INSERT INTO post_tags (post_id, tag_id)
SELECT p.post_id, t.tag_id
FROM gallery_posts p, tags t
WHERE p.title = '폭발 이펙트 애니메이션'
  AND t.tag_name IN ('애니메이션', '픽셀아트');

-- ── post_count 갱신 ───────────────────────────────────────
UPDATE tags SET post_count = (
    SELECT COUNT(*) FROM post_tags WHERE post_tags.tag_id = tags.tag_id
);

-- ── 에셋 태그 연결 ────────────────────────────────────────

-- 판타지 캐릭터 스프라이트 팩 → 판타지, RPG, 캐릭터, 무료
INSERT INTO asset_tags (asset_id, tag_id)
SELECT a.asset_id, t.tag_id
FROM assets a, tags t
WHERE a.title = '판타지 캐릭터 스프라이트 팩'
  AND t.tag_name IN ('판타지', 'RPG', '캐릭터', '무료');

-- 사이버펑크 타일셋 → 사이버펑크, 배경, 레트로
INSERT INTO asset_tags (asset_id, tag_id)
SELECT a.asset_id, t.tag_id
FROM assets a, tags t
WHERE a.title = '사이버펑크 타일셋'
  AND t.tag_name IN ('사이버펑크', '배경', '레트로');

-- 이펙트 스프라이트시트 모음 → 애니메이션, 귀여운, 픽셀아트
INSERT INTO asset_tags (asset_id, tag_id)
SELECT a.asset_id, t.tag_id
FROM assets a, tags t
WHERE a.title = '이펙트 스프라이트시트 모음'
  AND t.tag_name IN ('애니메이션', '귀여운', '픽셀아트');
