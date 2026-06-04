-- V18: 커미션 거래룸 채팅 테이블 생성 (Phase 3-a)
-- 커미션당 1:1 채팅방. 방은 채팅 첫 접근 시 지연 생성한다(commission_id UNIQUE로 1:1 보장).
-- 메시지는 방에 시간순으로 쌓이며, room_id+created_at 인덱스로 방별 조회를 최적화한다.

CREATE TABLE chat_rooms (
    room_id       BIGINT     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    commission_id BIGINT     NOT NULL UNIQUE REFERENCES commissions(commission_id),
    created_at    TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_messages (
    message_id BIGINT     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id    BIGINT     NOT NULL REFERENCES chat_rooms(room_id),
    sender_id  BIGINT     NOT NULL REFERENCES users(user_id),
    content    TEXT       NOT NULL CHECK (char_length(content) <= 2000),
    is_read    BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_messages_room_created ON chat_messages (room_id, created_at);
