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
import com.expansion.server.global.util.R2Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /** 프로필 이미지 최대 크기 — 원본을 그대로 저장하므로 용량으로 제한 */
    static final long MAX_PROFILE_IMAGE_BYTES = 2L * 1024 * 1024;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final FollowRepository followRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<R2Uploader> r2UploaderProvider;   // 로컬 R2 off면 빈 없음

    // ── 내 프로필 조회 ─────────────────────────────────────
    public UserProfileResponse getMyProfile(Long userId) {
        User user       = findUser(userId);
        Profile profile = findProfile(userId);
        return UserProfileResponse.ofMe(user, profile);
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
                request.getIsPublic()        != null ? request.getIsPublic()        : profile.isPublic()
        );

        return UserProfileResponse.ofMe(user, profile);
    }

    // ── 프로필 이미지 ──────────────────────────────────────
    /**
     * 프로필 이미지 업로드/교체 — 서버가 profiles/{userId}/avatar/ 에 저장하고 프로필에 반영.
     * 이전 이미지는 커밋 후 삭제, 트랜잭션이 롤백되면 방금 올린 이미지를 삭제(고아 방지).
     */
    @Transactional
    public UserProfileResponse uploadProfileImage(Long userId, MultipartFile file) {
        // 입력 검증을 R2 가용성 체크보다 먼저(잘못된 요청은 400/413, R2 off 로컬에서도 검증 가능)
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_PROFILE_IMAGE_BYTES) {
            throw new CustomException(ErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT, e);
        }
        // 클라이언트가 보낸 Content-Type이 아니라 실제 파일 시그니처로 판별(위장 업로드 방지)
        ImageType type = ImageType.detect(bytes);
        if (type == null) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_TYPE);
        }
        R2Uploader r2 = r2UploaderProvider.getIfAvailable();
        if (r2 == null) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_DISABLED);
        }

        User user       = findUser(userId);
        Profile profile = findProfile(userId);
        String oldUrl   = profile.getProfileImageUrl();

        String newUrl = r2.uploadBytes(bytes, type.contentType, type.extension, avatarFolder(userId));
        profile.changeProfileImage(newUrl);
        registerImageCleanup(r2, userId, oldUrl, newUrl);

        return UserProfileResponse.ofMe(user, profile);
    }

    /** 프로필 이미지 제거 — 기본(이니셜) 아바타로 돌아가고, 기존 파일은 커밋 후 삭제 */
    @Transactional
    public UserProfileResponse removeProfileImage(Long userId) {
        User user       = findUser(userId);
        Profile profile = findProfile(userId);
        String oldUrl   = profile.getProfileImageUrl();

        profile.changeProfileImage(null);
        R2Uploader r2 = r2UploaderProvider.getIfAvailable();
        if (r2 != null) {
            registerImageCleanup(r2, userId, oldUrl, null);
        }
        return UserProfileResponse.ofMe(user, profile);
    }

    private static String avatarFolder(Long userId) {
        return "profiles/" + userId + "/avatar";
    }

    /**
     * R2 정리 예약: 커밋되면 이전 이미지 삭제, 롤백되면 새로 올린 이미지 삭제.
     * 이전 이미지는 profiles/{userId}/ 경로일 때만 지움(경로 기반 소유권 — 남의 객체를 지우지 않음).
     * 삭제 실패는 로그만 남김(파일만 고아로 남고 프로필은 정상).
     */
    private void registerImageCleanup(R2Uploader r2, Long userId, String oldUrl, String newUrl) {
        String ownedPrefix = "profiles/" + userId + "/";
        String oldKey = r2.keyOf(oldUrl);
        boolean deleteOld = oldKey != null && oldKey.startsWith(ownedPrefix) && !Objects.equals(oldUrl, newUrl);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED && deleteOld) {
                    safeDelete(r2, oldUrl);
                } else if (status == STATUS_ROLLED_BACK && newUrl != null) {
                    safeDelete(r2, newUrl);
                }
            }
        });
    }

    private static void safeDelete(R2Uploader r2, String url) {
        try {
            r2.delete(url);
        } catch (RuntimeException e) {
            log.warn("[Profile] 프로필 이미지 R2 삭제 실패(무시) — url={}, err={}", url, e.getMessage());
        }
    }

    /** 허용 이미지 형식 — 파일 앞부분 시그니처(매직 바이트)로 판별. SVG는 스크립트를 담을 수 있어 제외 */
    enum ImageType {
        PNG("image/png", ".png"),
        JPEG("image/jpeg", ".jpg"),
        GIF("image/gif", ".gif"),
        WEBP("image/webp", ".webp");

        final String contentType;
        final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        static ImageType detect(byte[] b) {
            if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return PNG;
            if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) return JPEG;
            if (startsWith(b, 0, 'G', 'I', 'F', '8')) return GIF;
            if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) return WEBP;
            return null;
        }

        private static boolean startsWith(byte[] b, int offset, int... sig) {
            if (b.length < offset + sig.length) return false;
            for (int i = 0; i < sig.length; i++) {
                if ((b[offset + i] & 0xFF) != sig[i]) return false;
            }
            return true;
        }
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
