package com.expansion.server.domain.chat.service;

import com.expansion.server.domain.chat.dto.ChatMessageResponse;
import com.expansion.server.domain.chat.entity.ChatMessage;
import com.expansion.server.domain.chat.entity.ChatRoom;
import com.expansion.server.domain.chat.repository.ChatMessageRepository;
import com.expansion.server.domain.chat.repository.ChatRoomRepository;
import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CommissionRepository commissionRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    // 메시지 목록 — 방이 없으면 지연 생성하므로 쓰기 트랜잭션
    @Transactional
    public Page<ChatMessageResponse> getMessages(Long commissionId, Long userId, Pageable pageable) {
        ChatRoom room = getOrCreateRoom(commissionId, userId);
        Page<ChatMessage> page = chatMessageRepository.findByRoom_RoomId(room.getRoomId(), pageable);

        // 발신자 프로필 일괄 조회 (N+1 방지)
        List<Long> senderIds = page.getContent().stream()
                .map(m -> m.getSender().getUserId())
                .distinct()
                .toList();
        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(senderIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        return page.map(m -> ChatMessageResponse.of(m, profileMap.get(m.getSender().getUserId())));
    }

    // 메시지 전송
    @Transactional
    public ChatMessageResponse sendMessage(Long commissionId, Long userId, String content) {
        ChatRoom room = getOrCreateRoom(commissionId, userId);
        Commission commission = room.getCommission();

        // 종료된 계약(완료/취소)은 읽기 전용 — 새 메시지 전송 불가
        if ("COMPLETED".equals(commission.getStatus()) || "CANCELLED".equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .build());

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        return ChatMessageResponse.of(message, profile);
    }

    // 방 확보(지연 생성) + 권한 검증 — 해당 커미션의 의뢰자/작가만 접근 가능
    private ChatRoom getOrCreateRoom(Long commissionId, Long userId) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        if (!commission.getClient().getUserId().equals(userId)
                && !commission.getArtist().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        return chatRoomRepository.findByCommission_CommissionId(commissionId)
                .orElseGet(() -> {
                    try {
                        return chatRoomRepository.saveAndFlush(
                                ChatRoom.builder().commission(commission).build());
                    } catch (DataIntegrityViolationException e) {
                        // 동시 생성 race — commission_id UNIQUE 위반 시 이미 생성된 방 재조회
                        return chatRoomRepository.findByCommission_CommissionId(commissionId)
                                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));
                    }
                });
    }
}
