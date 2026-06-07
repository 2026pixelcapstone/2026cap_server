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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final ChatPresenceTracker presenceTracker;

    private static final int MAX_PAGE_SIZE = 100;

    // 메시지 목록 — 방이 없으면 지연 생성하므로 쓰기 트랜잭션
    @Transactional
    public Page<ChatMessageResponse> getMessages(Long commissionId, Long userId, Pageable pageable) {
        Commission commission = getAuthorizedCommission(commissionId, userId);
        ChatRoom room = getOrCreateRoom(commission);

        // 페이지 크기 상한 + 안정 정렬(createdAt, messageId): 동일 시각 메시지에서
        // 페이지 경계가 흔들려 중복/누락되는 것을 방지
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Pageable stable = PageRequest.of(
                pageable.getPageNumber(), size,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("messageId")));

        Page<ChatMessage> page = chatMessageRepository.findByRoom_RoomId(room.getRoomId(), stable);

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
        Commission commission = getAuthorizedCommission(commissionId, userId);

        // 종료된 계약(완료/취소)은 읽기 전용 — 방 생성 '전에' 검사해 실패 요청이 방을 만들지 않게
        if ("COMPLETED".equals(commission.getStatus()) || "CANCELLED".equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        ChatRoom room = getOrCreateRoom(commission);
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

    // 읽음 처리 — 상대가 보낸 안읽은 메시지를 읽음으로.
    // 새로 읽은 게 있으면 '읽은 최대 messageId(커서)'를, 없으면 null 반환
    @Transactional
    public Long markRead(Long commissionId, Long userId) {
        Commission commission = getAuthorizedCommission(commissionId, userId);
        ChatRoom room = getOrCreateRoom(commission);
        int updated = chatMessageRepository.markReadByOther(room.getRoomId(), userId);
        if (updated == 0) return null;
        return chatMessageRepository.findLastReadMessageIdFromOther(room.getRoomId(), userId);
    }

    // 현재 거래룸 접속자 조회 — 입장자가 접속 직후 초기 상태를 받기 위한 스냅샷(권한검증 포함)
    public List<Long> getPresence(Long commissionId, Long userId) {
        getAuthorizedCommission(commissionId, userId);
        return presenceTracker.getPresent(commissionId);
    }

    // 커미션 조회 + 권한 검증 (방 생성 없음) — 해당 커미션의 의뢰자/작가만 접근 가능
    private Commission getAuthorizedCommission(Long commissionId, Long userId) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        if (!commission.getClient().getUserId().equals(userId)
                && !commission.getArtist().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        return commission;
    }

    // 방 확보(지연 생성) — 권한·상태는 호출 전에 검증된 상태여야 함
    private ChatRoom getOrCreateRoom(Commission commission) {
        Long commissionId = commission.getCommissionId();
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
