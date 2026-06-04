package com.expansion.server.domain.chat.repository;

import com.expansion.server.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByCommission_CommissionId(Long commissionId);
}
