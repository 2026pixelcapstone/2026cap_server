package com.expansion.server.domain.chat.entity;

import com.expansion.server.domain.commission.entity.Commission;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    // 커미션당 1:1 (commission_id UNIQUE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_id", nullable = false, unique = true)
    private Commission commission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ChatRoom(Commission commission) {
        this.commission = commission;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
