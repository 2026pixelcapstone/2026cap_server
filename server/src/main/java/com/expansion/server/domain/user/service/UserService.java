package com.expansion.server.domain.user.service;

import com.expansion.server.domain.user.dto.ProfileUpdateRequest;
import com.expansion.server.domain.user.dto.UserProfileResponse;
import com.expansion.server.domain.notification.entity.NotificationType;
import com.expansion.server.domain.notification.event.NotificationEvent;
import com.expansion.server.domain.user.entity.Follow;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.FollowRepository;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final FollowRepository followRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ── 내 프로필 조회 ─────────────────────────────────────
    public UserProfileResponse getMyProfile(Long userId) {
        User user       = findUser(userId);
        Profile profile = findProfile(userId);
        return UserProfileResponse.of(user, profile, false);
    }

    // ── 타인 프로필 조회 ───────────────────────────────────
    public UserProfileResponse getUserProfile(Long targetId, Long currentUserId) {
        User user       = findUser(targetId);
        Profile profile = findProfile(targetId);
        boolean isFollowing = currentUserId != null &&
                followRepository.existsByFollower_UserIdAndFollowing_UserId(currentUserId, targetId);
        return UserProfileResponse.of(user, profile, isFollowing);
    }

    // ── 프로필 수정 ────────────────────────────────────────
    @Transactional
    public UserProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user       = findUser(userId);
        Profile profile = findProfile(userId);

        // 닉네임 변경 요청 시 중복 확인
        if (request.getNickname() != null
                && !request.getNickname().equals(profile.getNickname())
                && profileRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        profile.update(
                request.getNickname()        != null ? request.getNickname()        : profile.getNickname(),
                request.getBio(),
                request.getWebsiteUrl(),
                request.getProfileImageUrl(),
                request.getIsPublic()        != null ? request.getIsPublic()        : profile.isPublic()
        );

        return UserProfileResponse.of(user, profile, false);
    }

    // ── 팔로우 ─────────────────────────────────────────────
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new CustomException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        if (followRepository.existsByFollower_UserIdAndFollowing_UserId(followerId, followingId)) {
            throw new CustomException(ErrorCode.ALREADY_FOLLOWING);
        }

        User follower  = findUser(followerId);
        User following = findUser(followingId);

        followRepository.save(Follow.builder()
                .follower(follower)
                .following(following)
                .build());

        findProfile(followerId).increaseFollowingCount();
        findProfile(followingId).increaseFollowerCount();

        // 팔로우 당한 사용자에게 알림. targetId=팔로워 → 클릭 시 팔로워 프로필로 이동
        eventPublisher.publishEvent(NotificationEvent.of(
                followingId, followerId, NotificationType.FOLLOW, followerId));
    }

    // ── 언팔로우 ───────────────────────────────────────────
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        Follow follow = followRepository
                .findByFollower_UserIdAndFollowing_UserId(followerId, followingId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        followRepository.delete(follow);

        findProfile(followerId).decreaseFollowingCount();
        findProfile(followingId).decreaseFollowerCount();
    }

    // ── 팔로워 목록 ────────────────────────────────────────
    public List<UserProfileResponse> getFollowers(Long userId) {
        return followRepository.findAllByFollowing_UserId(userId).stream()
                .map(f -> {
                    Profile p = profileRepository.findByUser_UserId(f.getFollower().getUserId()).orElse(null);
                    return p != null ? UserProfileResponse.of(f.getFollower(), p, false) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ── 팔로잉 목록 ────────────────────────────────────────
    public List<UserProfileResponse> getFollowing(Long userId) {
        return followRepository.findAllByFollower_UserId(userId).stream()
                .map(f -> {
                    Profile p = profileRepository.findByUser_UserId(f.getFollowing().getUserId()).orElse(null);
                    return p != null ? UserProfileResponse.of(f.getFollowing(), p, false) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ── 닉네임으로 프로필 조회 ─────────────────────────────
    public UserProfileResponse getUserByNickname(String nickname, Long currentUserId) {
        Profile profile = profileRepository.findByNickname(nickname)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User user = profile.getUser();
        boolean isFollowing = currentUserId != null &&
                followRepository.existsByFollower_UserIdAndFollowing_UserId(currentUserId, user.getUserId());
        return UserProfileResponse.of(user, profile, isFollowing);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Profile findProfile(Long userId) {
        return profileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
