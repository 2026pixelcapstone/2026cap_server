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

    // 한 사용자가 참여 중인 모든 커미션에서 안읽은(상대가 보낸) 메시지 총합 — 알림 종 배지 집계용
    @Query("""
            SELECT COUNT(m) FROM ChatMessage m
            WHERE m.sender.userId <> :userId
            AND m.isRead = false
            AND (m.room.commission.client.userId = :userId
                 OR m.room.commission.artist.userId = :userId)
            """)
    long countAllUnreadForUser(@Param("userId") Long userId);

    // 안읽은 메시지가 있는 방별 [roomId, commissionId, clientId, artistId, unreadCount] — 알림 채팅 미리보기용
    // (partner는 서비스에서 client/artist 중 내가 아닌 쪽으로 결정. CASE를 GROUP BY에 넣으면
    //  Postgres가 파라미터 섞인 표현식 매칭을 거부하므로 raw 컬럼으로 그룹화)
    @Query("""
            SELECT m.room.roomId,
                   m.room.commission.commissionId,
                   m.room.commission.client.userId,
                   m.room.commission.artist.userId,
                   COUNT(m)
            FROM ChatMessage m
            WHERE m.sender.userId <> :userId
            AND m.isRead = false
            AND (m.room.commission.client.userId = :userId
                 OR m.room.commission.artist.userId = :userId)
            GROUP BY m.room.roomId,
                     m.room.commission.commissionId,
                     m.room.commission.client.userId,
                     m.room.commission.artist.userId
            """)
    List<Object[]> findUnreadRoomsForUser(@Param("userId") Long userId);

    // 주어진 방들의 최신 메시지(방별 max messageId) 배치 조회 — 미리보기 텍스트용
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.messageId IN (
                SELECT MAX(m2.messageId) FROM ChatMessage m2
                WHERE m2.room.roomId IN :roomIds
                GROUP BY m2.room.roomId
            )
            """)
    List<ChatMessage> findLatestMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);
}
