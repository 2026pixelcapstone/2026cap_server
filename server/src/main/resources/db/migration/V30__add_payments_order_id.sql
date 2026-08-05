-- V30: payments에 order_id 추가 (토스 결제 연동)
-- 결제 요청 전 서버가 orderId를 미리 발급·저장해두고, 승인(confirm) 시 돌아온 값과 대조해
-- 금액 위변조를 막는다(사전 저장값 기준 검증). 토스 orderId 규칙: 6~64자, 영문/숫자/-_.

ALTER TABLE payments ADD COLUMN order_id VARCHAR(64);

-- 같은 orderId 재사용 불가(멱등·중복 승인 방지). NULL은 여러 개 허용(과거 행/에셋 즉시결제 등).
CREATE UNIQUE INDEX uq_payments_order_id ON payments(order_id) WHERE order_id IS NOT NULL;
