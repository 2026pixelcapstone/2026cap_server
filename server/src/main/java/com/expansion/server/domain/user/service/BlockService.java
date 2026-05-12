package com.expansion.server.domain.user.service;

import com.expansion.server.domain.user.dto.BlockedUserInfo;
import com.expansion.server.domain.user.dto.BlockResponse;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.entity.UserBlock;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserBlockRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private static final String TYPE_USER = "USER";
    private static final String TYPE_TAG  = "TAG";

    private final UserBlockRepository userBlockRepository;
    private final UserRepository      userRepository;
    private final ProfileRepository   profileRepository;

    // ──────────────────────────────────────────────
    // 차단 목록 조회
    // ──────────────────────────────────────────────

    public BlockResponse getMyBlocks(Long userId) {
        List<UserBlock> blocks = userBlockRepository.findByUser_UserId(userId);

        // 차단된 유저 ID 목록 추출
        List<Long> blockedUserIdList = blocks.stream()
                .filter(b -> TYPE_USER.equals(b.getBlockType()))
                .map(b -> b.getTargetUser().getUserId())
                .toList();

        // 프로필 일괄 조회 (N+1 방지)
        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(blockedUserIdList)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        List<BlockedUserInfo> blockedUsers = blockedUserIdList.stream()
                .map(uid -> {
                    Profile profile = profileMap.get(uid);
                    return BlockedUserInfo.builder()
                            .userId(uid)
                            .nickname(profile != null ? profile.getNickname() : "알 수 없는 사용자")
                            .profileImageUrl(profile != null ? profile.getProfileImageUrl() : null)
                            .build();
                })
                .toList();

        List<String> blockedTags = blocks.stream()
                .filter(b -> TYPE_TAG.equals(b.getBlockType()))
                .map(UserBlock::getTargetTag)
                .toList();

        return BlockResponse.builder()
                .blockedUsers(blockedUsers)
                .blockedTags(blockedTags)
                .build();
    }

    // ──────────────────────────────────────────────
    // 사용자 차단
    // ──────────────────────────────────────────────

    @Transactional
    public void blockUser(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 이미 차단 중이면 무시
        boolean exists = userBlockRepository
                .findByUser_UserIdAndBlockTypeAndTargetUser_UserId(userId, TYPE_USER, targetUserId)
                .isPresent();
        if (exists) return;

        User user       = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        try {
            userBlockRepository.save(UserBlock.builder()
                    .user(user)
                    .blockType(TYPE_USER)
                    .targetUser(targetUser)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // 동시 요청으로 인한 중복 저장 시도 — 무시
        }
    }

    @Transactional
    public void unblockUser(Long userId, Long targetUserId) {
        userBlockRepository.deleteByUser_UserIdAndBlockTypeAndTargetUser_UserId(
                userId, TYPE_USER, targetUserId);
    }

    // ──────────────────────────────────────────────
    // 태그 차단
    // ──────────────────────────────────────────────

    @Transactional
    public void blockTag(Long userId, String tagName) {
        // 태그 입력값 검증
        String trimmed = (tagName == null) ? "" : tagName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 이미 차단 중이면 무시
        boolean exists = userBlockRepository
                .findByUser_UserIdAndBlockTypeAndTargetTag(userId, TYPE_TAG, trimmed)
                .isPresent();
        if (exists) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        try {
            userBlockRepository.save(UserBlock.builder()
                    .user(user)
                    .blockType(TYPE_TAG)
                    .targetTag(trimmed)
                    .build());
        } catch (DataIntegrityViolationException ignored) {
            // 동시 요청으로 인한 중복 저장 시도 — 무시
        }
    }

    @Transactional
    public void unblockTag(Long userId, String tagName) {
        String trimmed = (tagName == null) ? "" : tagName.trim();
        userBlockRepository.deleteByUser_UserIdAndBlockTypeAndTargetTag(
                userId, TYPE_TAG, trimmed);
    }
}
