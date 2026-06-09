package com.expansion.server.domain.chat.service;

import com.expansion.server.domain.chat.dto.ChatMessagePage;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private static final int DEFAULT_PAGE_SIZE = 30;

    // 메시지 목록 (커서 페이지네이션) — 방이 없으면 지연 생성하므로 쓰기 트랜잭션
    // beforeId=null → 최신 size개, beforeId 있으면 그보다 이전 size개("위로 더보기").
    // messageId DESC로 받아 화면 표시용으로 오름차순(오래된→최신) 뒤집어 반환.
    @Transactional
    public ChatMessagePage getMessages(Long commissionId, Long userId, Long beforeId, int size) {
        Commission commission = getAuthorizedCommission(commissionId, userId);
        ChatRoom room = getOrCreateRoom(commission);

        int limit = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        // hasMore 판정용으로 limit+1개 조회 (별도 count 쿼리 불필요)
        List<ChatMessage> rows = new ArrayList<>(
                chatMessageRepository.findPage(room.getRoomId(), beforeId, PageRequest.of(0, limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }

        // DESC(최신 우선)로 받았으니 화면 표시용 오름차순으로 뒤집기
        Collections.reverse(rows);

        // 발신자 프로필 일괄 조회 (N+1 방지)
        List<Long> senderIds = rows.stream()
                .map(m -> m.getSender().getUserId())
                .distinct()
                .toList();
        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(senderIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        List<ChatMessageResponse> content = rows.stream()
                .map(m -> ChatMessageResponse.of(m, profileMap.get(m.getSender().getUserId())))
                .toList();

        return new ChatMessagePage(content, hasMore);
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

    // 커미션별 안읽음 메시지 수 — commissionId → count (배치). 없는 커미션은 결과에 미포함(0으로 간주)
    public Map<Long, Long> getUnreadCounts(List<Long> commissionIds, Long userId) {
        if (commissionIds == null || commissionIds.isEmpty()) return Map.of();
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : chatMessageRepository.countUnreadByCommission(commissionIds, userId)) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) continue;
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
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
