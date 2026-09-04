-- V18: 커미션 거래룸 채팅 테이블 생성 (Phase 3-a)
-- 커미션당 1:1 채팅방. 방은 채팅 첫 접근 시 지연 생성한다(commission_id UNIQUE로 1:1 보장).
-- 메시지는 방에 시간순으로 쌓이며, room_id+created_at 인덱스로 방별 조회를 최적화한다.

ALTER TABLE chat_messages ADD CHECK (char_length(content) <= 2000);

CREATE INDEX idx_chat_messages_room_created ON chat_messages (room_id, created_at);
