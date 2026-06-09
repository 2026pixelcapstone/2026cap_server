package com.expansion.server.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 채팅 메시지 커서 페이지.
 * messages: 화면 표시용 오름차순(오래된→최신).
 * hasMore: 반환된 가장 오래된 메시지보다 더 이전 메시지가 존재하는지(= "위로 더보기" 가능 여부).
 */
@Getter
@AllArgsConstructor
public class ChatMessagePage {
    private List<ChatMessageResponse> messages;
    private boolean hasMore;
}
