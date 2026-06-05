package com.expansion.server.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * WebSocket 토픽(/topic/commissions/{id})으로 흘려보내는 이벤트 봉투.
 * type으로 메시지/읽음/접속 등을 구분한다. (PRESENCE는 #1.5에서 추가 예정)
 */
@Getter
@Builder
public class ChatEvent {

    private String type;                 // MESSAGE / READ
    private ChatMessageResponse message; // type=MESSAGE
    private Long readerId;               // type=READ — 상대 메시지를 읽은 사용자

    public static ChatEvent message(ChatMessageResponse message) {
        return ChatEvent.builder().type("MESSAGE").message(message).build();
    }

    public static ChatEvent read(Long readerId) {
        return ChatEvent.builder().type("READ").readerId(readerId).build();
    }
}
