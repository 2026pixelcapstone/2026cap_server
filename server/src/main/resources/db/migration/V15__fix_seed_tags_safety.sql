-- V15: 시드 태그 연결 안전성 개선
-- V14의 INSERT를 ON CONFLICT DO NOTHING + email 기반 식별로 보완
-- (V14가 이미 적용된 환경에서 중복 방지 및 재실행 안전성 확보)

-- ── 갤러리 게시물 태그 추가 보완 ─────────────────────────

INSERT INTO post_tags (post_id, tag_id)
SELECT p.post_id, t.tag_id
FROM gallery_posts p
JOIN users u ON p.user_id = u.user_id
, tags t
WHERE u.email = 'spriteknight@test.com'
  AND p.title = '판타지 기사 스프라이트'
  AND t.tag_name IN ('판타지', 'RPG', '캐릭터', '픽셀아트')
ON CONFLICT (post_id, tag_id) DO NOTHING;

INSERT INTO post_tags (post_id, tag_id)
SELECT p.post_id, t.tag_id
FROM gallery_posts p
JOIN users u ON p.user_id = u.user_id
, tags t
WHERE u.email = 'pixelwitch@test.com'
  AND p.title = '사이버펑크 시티 배경'
  AND t.tag_name IN ('사이버펑크', '배경', '픽셀아트')
ON CONFLICT (post_id, tag_id) DO NOTHING;

INSERT INTO post_tags (post_id, tag_id)
SELECT p.post_id, t.tag_id
FROM gallery_posts p
JOIN users u ON p.user_id = u.user_id
, tags t
WHERE u.email = 'neonbrush@test.com'
  AND p.title = '폭발 이펙트 애니메이션'
  AND t.tag_name IN ('애니메이션', '픽셀아트')
ON CONFLICT (post_id, tag_id) DO NOTHING;

-- ── 에셋 태그 추가 보완 ───────────────────────────────────

INSERT INTO asset_tags (asset_id, tag_id)
SELECT a.asset_id, t.tag_id
FROM assets a
JOIN users u ON a.user_id = u.user_id
, tags t
WHERE u.email = 'spriteknight@test.com'
  AND a.title = '판타지 캐릭터 스프라이트 팩'
  AND t.tag_name IN ('판타지', 'RPG', '캐릭터', '무료')
ON CONFLICT (asset_id, tag_id) DO NOTHING;

INSERT INTO asset_tags (asset_id, tag_id)
SELECT a.asset_id, t.tag_id
FROM assets a
JOIN users u ON a.user_id = u.user_id
, tags t
WHERE u.email = 'pixelwitch@test.com'
  AND a.title = '사이버펑크 타일셋'
  AND t.tag_name IN ('사이버펑크', '배경', '레트로')
ON CONFLICT (asset_id, tag_id) DO NOTHING;

INSERT INTO asset_tags (asset_id, tag_id)
SELECT a.asset_id, t.tag_id
FROM assets a
JOIN users u ON a.user_id = u.user_id
, tags t
WHERE u.email = 'neonbrush@test.com'
  AND a.title = '이펙트 스프라이트시트 모음'
  AND t.tag_name IN ('애니메이션', '귀여운', '픽셀아트')
ON CONFLICT (asset_id, tag_id) DO NOTHING;

-- ── post_count 재갱신 ─────────────────────────────────────
UPDATE tags SET post_count = (
    SELECT COUNT(*) FROM post_tags WHERE post_tags.tag_id = tags.tag_id
);
