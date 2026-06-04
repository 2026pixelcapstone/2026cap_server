package com.expansion.server.domain.chat.dto;

import com.expansion.server.domain.chat.entity.ChatMessage;
import com.expansion.server.domain.user.entity.Profile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponse {

    private Long messageId;
    private Long senderId;
    private String senderNickname;
    private String senderProfileImageUrl;
    private String content;
    private boolean isRead;
    private LocalDateTime createdAt;

    public static ChatMessageResponse of(ChatMessage message, Profile senderProfile) {
        return ChatMessageResponse.builder()
                .messageId(message.getMessageId())
                .senderId(message.getSender().getUserId())
                .senderNickname(senderProfile != null ? senderProfile.getNickname() : null)
                .senderProfileImageUrl(senderProfile != null ? senderProfile.getProfileImageUrl() : null)
                .content(message.getContent())
                .isRead(message.isRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
