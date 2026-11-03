-- V40: 무료 에셋이 '0원짜리 유료'(is_free=false, price=0)로 저장되던 데이터 바로잡기.
-- 원인: AssetCreateRequest의 boolean isFree가 JSON 'isFree'를 받지 못해 등록 시 항상 false로 저장됨(코드는 같은 PR에서 수정).
-- 화면·다운로드는 가격 0을 무료로 보고 있었지만, 스토어 '무료' 필터·목록 isFree 값이 틀렸다.
-- 규칙(AssetService.resolveFree와 동일): 무료 = is_free 또는 가격 0·없음, 무료면 가격 0.
-- 한계: '무료로 표시했지만 가격을 함께 보낸' 등록은 is_free=false·가격>0으로 저장돼 진짜 유료와 구분할 근거가 없어 고치지 않는다
--       (프론트는 무료 선택 시 가격 0을 보내므로 실제로는 드묾). 가격>0 행은 일괄 변경하지 않음.

UPDATE assets
SET is_free = TRUE, price = 0
WHERE is_free = FALSE AND (price IS NULL OR price = 0);

UPDATE assets
SET price = 0
WHERE is_free = TRUE AND price IS NOT NULL AND price <> 0;
