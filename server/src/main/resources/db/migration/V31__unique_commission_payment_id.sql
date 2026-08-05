-- V31: 커미션 ↔ 결제 1:1 보장
-- payment_id로 커미션을 역참조(PaymentService.confirm)하므로, 한 결제가 여러 커미션에
-- 연결되면 안 된다. 부분 유니크 인덱스로 강제(payment_id 없는 무료/미결제 커미션은 다수 허용).

CREATE UNIQUE INDEX uq_commissions_payment_id
    ON commissions(payment_id) WHERE payment_id IS NOT NULL;
