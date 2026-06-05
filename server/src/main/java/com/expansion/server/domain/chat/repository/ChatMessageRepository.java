package com.expansion.server.domain.chat.repository;

import com.expansion.server.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByRoom_RoomId(Long roomId, Pageable pageable);

    // 방에서 '내가 보낸 게 아닌' 안읽은 메시지를 읽음 처리 → 갱신된 건수 반환
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ChatMessage m SET m.isRead = true
            WHERE m.room.roomId = :roomId
            AND m.sender.userId <> :userId
            AND m.isRead = false
            """)
    int markReadByOther(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
