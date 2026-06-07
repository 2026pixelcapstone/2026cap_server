package com.expansion.server.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * WebSocket 토픽(/topic/commissions/{id})으로 흘려보내는 이벤트 봉투.
 * type으로 메시지/읽음/접속을 구분한다.
 */
@Getter
@Builder
public class ChatEvent {

    private String type;                 // MESSAGE / READ / PRESENCE
    private ChatMessageResponse message; // type=MESSAGE
    private Long readerId;               // type=READ — 상대 메시지를 읽은 사용자
    private Long lastReadMessageId;      // type=READ — 이 messageId 이하만 읽음으로 간주(동시성 오표시 방지)
    private List<Long> presentUserIds;   // type=PRESENCE — 현재 거래룸에 있는 사용자들

    public static ChatEvent message(ChatMessageResponse message) {
        return ChatEvent.builder().type("MESSAGE").message(message).build();
    }

    public static ChatEvent read(Long readerId, Long lastReadMessageId) {
        return ChatEvent.builder()
                .type("READ")
                .readerId(readerId)
                .lastReadMessageId(lastReadMessageId)
                .build();
    }

    public static ChatEvent presence(List<Long> presentUserIds) {
        return ChatEvent.builder().type("PRESENCE").presentUserIds(presentUserIds).build();
    }
}
