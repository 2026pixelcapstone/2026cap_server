package com.expansion.server.domain.chat.repository;

import com.expansion.server.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByRoom_RoomId(Long roomId, Pageable pageable);

    // 커서 페이지네이션 — beforeId가 null이면 최신부터, 있으면 그보다 이전(messageId<beforeId)만.
    // messageId DESC(최신 우선) 정렬, limit은 Pageable로(hasMore 판정 위해 size+1 조회).
    // ※ beforeId는 단순 비교라 CAST 불필요(문자열 함수 래핑 아님)
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.room.roomId = :roomId
            AND (:beforeId IS NULL OR m.messageId < :beforeId)
            ORDER BY m.messageId DESC
            """)
    List<ChatMessage> findPage(@Param("roomId") Long roomId,
                               @Param("beforeId") Long beforeId,
                               Pageable pageable);

    // 방에서 '내가 보낸 게 아닌' 안읽은 메시지를 읽음 처리 → 갱신된 건수 반환
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ChatMessage m SET m.isRead = true
            WHERE m.room.roomId = :roomId
            AND m.sender.userId <> :userId
            AND m.isRead = false
            """)
    int markReadByOther(@Param("roomId") Long roomId, @Param("userId") Long userId);

    // 내가 읽은(상대가 보낸) 메시지의 최대 messageId — READ 이벤트 커서용
    @Query("""
            SELECT MAX(m.messageId) FROM ChatMessage m
            WHERE m.room.roomId = :roomId
            AND m.sender.userId <> :userId
            AND m.isRead = true
            """)
    Long findLastReadMessageIdFromOther(@Param("roomId") Long roomId, @Param("userId") Long userId);

    // 커미션별 안읽음 메시지 수(상대가 보낸 것 중 미읽음) 배치 집계 — [commissionId, count] 행
    @Query("""
            SELECT m.room.commission.commissionId, COUNT(m)
            FROM ChatMessage m
            WHERE m.room.commission.commissionId IN :commissionIds
            AND m.sender.userId <> :userId
            AND m.isRead = false
            GROUP BY m.room.commission.commissionId
            """)
    List<Object[]> countUnreadByCommission(@Param("commissionIds") List<Long> commissionIds,
                                           @Param("userId") Long userId);
}
