-- V18: 커미션 거래룸 채팅 테이블 (Phase 3-a)
-- ⚠️ chat_rooms/chat_messages 테이블 자체는 V6(create_commission_tables)에서 이미 생성됨.
--    (2026-06 커미션 재구축 때 V6에 chat 테이블이 추가되며 중복이 생겼고, 새 DB를 처음부터
--     만들 때만 V18의 CREATE가 "already exists"로 충돌했음 — TROUBLESHOOTING #28)
-- 그래서 V18은 V6 대비 실제 추가분(2000자 CHECK 제약 + 방별 조회 인덱스)만 반영한다.
--
-- 🔴 이 파일은 이미 배포된 마이그레이션이라 수정 시 기존 DB의 Flyway 체크섬이 달라진다.
--    → FlywayConfig의 repair→migrate 전략으로 전 환경 체크섬을 1회 자동 갱신한다.
--    기존 DB엔 이미 제약·인덱스가 있어 재실행 없이 통과하고, 새 DB는 아래 구문이 정상 실행된다.

ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_messages_content_length CHECK (char_length(content) <= 2000);

CREATE INDEX idx_chat_messages_room_created ON chat_messages (room_id, created_at);
