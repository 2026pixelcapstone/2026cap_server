-- V34: 에셋 소유권(asset_purchases) — 구매마다 행을 남기고 '활성(ACTIVE) 소유는 1개만' 보장
--
-- 1) 기존 UNIQUE(user_id, asset_id)는 사람·에셋당 행 1개만 허용 → 환불(REFUNDED) 후 재구매 시 새 행을 못 넣음.
--    부분 유니크 인덱스로 교체: ACTIVE 행만 1개로 제한하고, REFUNDED 행(구매·환불 이력)은 여러 개 보존.
-- 2) 무료 취득도 소유권 행으로 기록(payment_id NULL, price_paid 0) — 무료→유료 전환 후에도 무료일 때 받은 사람의 권리 유지.
--    이후 신규 무료 다운로드는 서버가 다운로드 시점에 기록하고, 여기서는 기존 다운로드 기록만 한 번 채운다.
--    ※ 이 행은 매출이 아니다. 판매 집계는 반드시 payment_id IS NOT NULL로 거를 것.

-- V5에서 이름 없이 만든 UNIQUE(user_id, asset_id) — Postgres 자동 이름
ALTER TABLE asset_purchases DROP CONSTRAINT IF EXISTS asset_purchases_user_id_asset_id_key;

CREATE UNIQUE INDEX uq_asset_purchases_active
    ON asset_purchases (user_id, asset_id)
    WHERE status = 'ACTIVE';

-- 기존 무료 다운로드 기록 → 무료 취득 행. 현재 무료인 에셋만(유료 에셋의 과거 이력은 알 수 없어 채우지 않음), 작성자 본인 제외.
INSERT INTO asset_purchases (user_id, asset_id, payment_id, price_paid, status, created_at, updated_at)
SELECT d.user_id, d.asset_id, NULL, 0, 'ACTIVE', d.created_at, d.created_at
FROM asset_downloads d
JOIN assets a ON a.asset_id = d.asset_id
WHERE (a.is_free OR a.price = 0)
  AND a.user_id <> d.user_id
ON CONFLICT (user_id, asset_id) WHERE status = 'ACTIVE' DO NOTHING;
