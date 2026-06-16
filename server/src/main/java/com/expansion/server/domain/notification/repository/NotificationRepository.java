package com.expansion.server.domain.notification.repository;

import com.expansion.server.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 커서(keyset) 페이지네이션 — beforeId보다 작은(=더 과거) 알림을 최신순으로.
     * beforeId=null이면 최신부터. hasMore 판정을 위해 호출 측에서 size+1을 요청한다.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
            AND (:beforeId IS NULL OR n.notificationId < :beforeId)
            AND (:unreadOnly = false OR n.isRead = false)
            ORDER BY n.notificationId DESC
            """)
    List<Notification> findPage(@Param("userId") Long userId,
                                @Param("beforeId") Long beforeId,
                                @Param("unreadOnly") boolean unreadOnly,
                                Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    Optional<Notification> findByNotificationIdAndUserId(Long notificationId, Long userId);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP
            WHERE n.userId = :userId AND n.isRead = false
            """)
    int markAllRead(@Param("userId") Long userId);
}
