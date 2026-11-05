package com.expansion.server.domain.user.service;

import com.expansion.server.domain.asset.service.AssetService;
import com.expansion.server.domain.commission.service.CommissionService;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.expansion.server.global.security.UserStatusChecker;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 회원 탈퇴 — 행은 지우지 않고(users를 참조하는 FK 39개, 대부분 CASCADE 없음) 개인정보를 지우고 활동을 정리한다.
 *
 * 남기는 것: 거래(commissions·채팅·납품 파일·리뷰)·결제·구매 기록, 댓글 — 닉네임이 '탈퇴한사용자_{id}'로 보임.
 * 정리하는 것: 작품 비공개, 에셋은 기존 삭제 규칙(구매자 있으면 판매 중지), 팔레트 숨김, 작가 서비스·의뢰글 마감,
 *   대기 중 지원 삭제, 프로젝트 삭제 표시, 진행 중 챌린지 참가 취소, 좋아요·팔로우·차단·알림·토큰 삭제(숫자 보정).
 * 막는 것: 진행 중 거래(결제 대기·작업 중·검토)가 있으면 탈퇴 불가 — 보관 중인 돈이 걸린 거래를 혼자 끝낼 수 없게.
 *
 * 표 하나하나에 저장소 메서드를 만드는 대신 여기서 SQL로 한 번에 — 탈퇴 때 무엇을 건드리는지 이 파일 하나로 보이게.
 * 새로 users를 참조하는 테이블을 만들면 여기에 처리를 추가할 것.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountWithdrawalService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CommissionService commissionService;
    private final AssetService assetService;
    private final UserService userService;
    private final UserStatusChecker userStatusChecker;
    private final EntityManager em;

    @Transactional
    public void withdraw(Long userId, String password, String confirmNickname) {
        // 사용자 행 잠금 — 같은 계정의 동시 탈퇴·로그인 갱신 등과 섞이지 않게
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        Profile profile = profileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1) 본인 확인 — 비밀번호 계정은 비밀번호, 소셜 전용 계정은 닉네임 직접 입력
        if (user.hasPassword()) {
            if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new CustomException(ErrorCode.WITHDRAWAL_PASSWORD_MISMATCH);
            }
        } else if (confirmNickname == null || !confirmNickname.equals(profile.getNickname())) {
            throw new CustomException(ErrorCode.WITHDRAWAL_CONFIRM_MISMATCH);
        }

        // 2) 진행 중 거래가 있으면 막음
        if (!commissionService.getMyActiveCommissions(userId).isEmpty()) {
            throw new CustomException(ErrorCode.WITHDRAWAL_ACTIVE_COMMISSION);
        }

        // 3) 에셋 — 기존 삭제 규칙 그대로(결제 이력 있으면 판매 중지·구매자는 계속 다운로드, 없으면 완전 삭제)
        @SuppressWarnings("unchecked")
        List<Number> assetIds = em.createNativeQuery(
                        "SELECT asset_id FROM assets WHERE user_id = :u AND status = 'ACTIVE'")
                .setParameter("u", userId).getResultList();
        assetIds.forEach(id -> assetService.deleteAsset(userId, id.longValue()));

        // 4) 프로필 사진 — 기존 경로로 R2까지 정리(커밋 후 삭제)
        if (profile.getProfileImageUrl() != null) {
            userService.removeProfileImage(userId);
        }

        // 위 JPA 변경을 먼저 DB에 반영한 뒤 일괄 SQL(TROUBLESHOOTING #40 — 대기 변경이 섞이지 않게)
        em.flush();

        // 5) 좋아요 — 대상 숫자를 먼저 줄이고 행 삭제
        exec("UPDATE gallery_posts SET like_count = GREATEST(like_count - 1, 0) WHERE post_id IN "
                + "(SELECT target_id FROM likes WHERE user_id = :u AND target_type = 'GALLERY_POST')", userId);
        exec("UPDATE assets SET like_count = GREATEST(like_count - 1, 0) WHERE asset_id IN "
                + "(SELECT target_id FROM likes WHERE user_id = :u AND target_type = 'ASSET')", userId);
        exec("UPDATE palettes SET like_count = GREATEST(like_count - 1, 0) WHERE palette_id IN "
                + "(SELECT target_id FROM likes WHERE user_id = :u AND target_type = 'PALETTE')", userId);
        exec("DELETE FROM likes WHERE user_id = :u", userId);
        exec("DELETE FROM favorites WHERE user_id = :u", userId);

        // 6) 팔로우 — 상대 쪽 숫자 보정 후 양방향 삭제
        exec("UPDATE profiles SET follower_count = GREATEST(follower_count - 1, 0) WHERE user_id IN "
                + "(SELECT following_id FROM follows WHERE follower_id = :u)", userId);
        exec("UPDATE profiles SET following_count = GREATEST(following_count - 1, 0) WHERE user_id IN "
                + "(SELECT follower_id FROM follows WHERE following_id = :u)", userId);
        exec("DELETE FROM follows WHERE follower_id = :u OR following_id = :u", userId);

        // 7) 차단·알림·토큰
        exec("DELETE FROM user_blocks WHERE user_id = :u OR target_user_id = :u", userId);
        exec("DELETE FROM notifications WHERE user_id = :u", userId);   // 내가 보낸 알림은 남김(닉네임이 탈퇴한 사용자로 보임)
        exec("DELETE FROM refresh_tokens WHERE user_id = :u", userId);
        exec("DELETE FROM email_verification_tokens WHERE user_id = :u", userId);
        exec("DELETE FROM password_reset_tokens WHERE user_id = :u", userId);

        // 8) 콘텐츠 — 작품 비공개, 팔레트 숨김, 프로젝트 삭제 표시, 공동 작업·멤버 해제
        exec("UPDATE gallery_posts SET visibility = 'PRIVATE' WHERE user_id = :u", userId);
        exec("UPDATE palettes SET status = 'DELETED' WHERE user_id = :u", userId);
        exec("UPDATE projects SET status = 'DELETED' WHERE user_id = :u", userId);
        exec("DELETE FROM project_members WHERE user_id = :u", userId);
        exec("DELETE FROM post_collaborators WHERE user_id = :u", userId);
        // 아직 판정 안 된 챌린지 참가만 취소(지난 순위는 '볼 수 없는 작품' 빈자리로 남음)
        exec("DELETE FROM challenge_entries WHERE user_id = :u AND challenge_id IN "
                + "(SELECT challenge_id FROM challenges WHERE judged_at IS NULL)", userId);
        // 공동 캔버스 — 쿨다운 행만 삭제(찍은 칸·기록은 판의 일부라 남김)
        exec("DELETE FROM canvas_cooldowns WHERE user_id = :u", userId);

        // 9) 커미션 — 열린 서비스·의뢰글 마감, 대기 중인 내 지원 삭제(수락된 지원은 거래 기록이라 남김)
        exec("UPDATE commission_services SET status = 'CLOSED' WHERE artist_id = :u AND status = 'OPEN'", userId);
        exec("UPDATE request_posts SET status = 'CLOSED' WHERE client_id = :u AND status = 'OPEN'", userId);
        exec("DELETE FROM commission_applications WHERE artist_id = :u AND status = 'PENDING'", userId);

        // 10) 계정·프로필 익명화
        user.withdraw();
        profile.anonymize(userId);

        // 커밋된 뒤 상태 캐시를 비움 — 커밋 전에 비우면 그 사이 요청이 ACTIVE를 다시 캐시할 수 있음
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                userStatusChecker.evict(userId);
            }
        });
        log.info("[WITHDRAWAL] 회원 탈퇴 완료 — userId={}, 정리한 에셋 {}개", userId, assetIds.size());
    }

    private void exec(String sql, Long userId) {
        em.createNativeQuery(sql).setParameter("u", userId).executeUpdate();
    }
}
