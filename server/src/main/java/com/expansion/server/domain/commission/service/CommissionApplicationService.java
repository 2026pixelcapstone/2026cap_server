package com.expansion.server.domain.commission.service;

import com.expansion.server.domain.commission.dto.CommissionApplicationCreateRequest;
import com.expansion.server.domain.commission.dto.CommissionApplicationResponse;
import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.commission.entity.CommissionApplication;
import com.expansion.server.domain.commission.entity.RequestPost;
import com.expansion.server.domain.commission.repository.CommissionApplicationRepository;
import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.domain.commission.repository.RequestPostRepository;
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
public class CommissionApplicationService {

    private final CommissionApplicationRepository applicationRepository;
    private final RequestPostRepository requestPostRepository;
    private final CommissionRepository commissionRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    // 지원하기
    @Transactional
    public CommissionApplicationResponse apply(Long artistId, CommissionApplicationCreateRequest request) {
        User artist = userRepository.findById(artistId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        RequestPost post = requestPostRepository.findById(request.getRequestPostId())
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        // 자신의 의뢰에 지원 불가
        if (post.getClient().getUserId().equals(artistId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // 마감된 의뢰에 지원 불가
        if ("CLOSED".equals(post.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        // 중복 지원 불가
        if (applicationRepository.existsByRequestPost_RequestPostIdAndArtist_UserId(
                post.getRequestPostId(), artistId)) {
            throw new CustomException(ErrorCode.ALREADY_APPLIED);
        }

        CommissionApplication application = CommissionApplication.builder()
                .requestPost(post)
                .artist(artist)
                .message(request.getMessage())
                .proposedPrice(request.getProposedPrice())
                .build();

        try {
            applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException e) {
            // unique constraint (request_post_id, artist_id) 위반 — 동시 중복 지원
            throw new CustomException(ErrorCode.ALREADY_APPLIED);
        }

        Profile artistProfile = profileRepository.findByUser_UserId(artistId).orElse(null);
        return CommissionApplicationResponse.of(application, artistProfile);
    }

    // 작가의 내 지원 목록
    public Page<CommissionApplicationResponse> getMyApplications(Long artistId, Pageable pageable) {
        Profile artistProfile = profileRepository.findByUser_UserId(artistId).orElse(null);
        return applicationRepository.findByArtist_UserId(artistId, pageable)
                .map(app -> CommissionApplicationResponse.of(app, artistProfile));
    }

    // 의뢰자가 자신의 의뢰에 온 지원 목록 조회
    public Page<CommissionApplicationResponse> getApplicationsByPost(Long clientId, Long requestPostId, Pageable pageable) {
        RequestPost post = requestPostRepository.findById(requestPostId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        // 의뢰자 본인만 조회 가능
        if (!post.getClient().getUserId().equals(clientId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Page<CommissionApplication> page = applicationRepository
                .findByRequestPost_RequestPostId(requestPostId, pageable);

        // N+1 방지: artistId 모아서 한 번에 조회
        List<Long> artistIds = page.getContent().stream()
                .map(app -> app.getArtist().getUserId())
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(artistIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        // 지원 → 생성된 커미션 매핑 (거래룸 바로가기/취소 상태 표시용, N+1 방지)
        List<Long> applicationIds = page.getContent().stream()
                .map(CommissionApplication::getApplicationId)
                .collect(Collectors.toList());

        Map<Long, Commission> commissionMap = commissionRepository.findByApplicationIdIn(applicationIds)
                .stream()
                .collect(Collectors.toMap(Commission::getApplicationId, c -> c, (a, b) -> a));

        return page.map(app -> {
            Commission commission = commissionMap.get(app.getApplicationId());
            return CommissionApplicationResponse.of(
                    app,
                    profileMap.get(app.getArtist().getUserId()),
                    commission != null ? commission.getCommissionId() : null,
                    commission != null ? commission.getStatus() : null);
        });
    }

    // 지원 수락 → 나머지 REJECTED + Commission 생성 → 생성된 커미션 ID 반환
    @Transactional
    public Long accept(Long clientId, Long applicationId) {
        // 비관적 락으로 동시 수락 방지
        CommissionApplication application = findByIdWithLock(applicationId);

        RequestPost post = application.getRequestPost();

        // 의뢰자 본인만 수락 가능
        if (!post.getClient().getUserId().equals(clientId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // 이미 처리된 지원
        if (!"PENDING".equals(application.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }
        // 마감된 의뢰는 수락 불가
        // [known limitation] findByIdWithLock는 지원서 행만 잠그고 RequestPost는 잠그지 않음.
        //  accept()와 RequestPostService.close()가 동시에 실행되면 이 CLOSED 검사를 통과한 뒤
        //  close()가 같은 지원을 REJECTED로 바꾼 채 커미션이 저장돼 불일치가 생길 수 있음.
        //  두 경로 모두 '의뢰자 본인'만 호출하고 결제도 없는 MVP라 발생 확률·피해가 낮아 보류.
        //  결제(에스크로) 도입 시 RequestPost 비관적 락/버전 기반 재검증으로 직렬화 예정.
        if ("CLOSED".equals(post.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        // 수락 처리 (복수 선택 허용 — 다른 지원 자동 거절 X, 의뢰글 자동 마감 X.
        //  의뢰자가 직접 '의뢰 마감' 시 남은 PENDING 지원이 일괄 거절됨)
        application.accept();

        // Commission 레코드 생성
        User client = post.getClient();
        User artist = application.getArtist();

        Commission commission = Commission.builder()
                .commissionType("REQUEST")
                .client(client)
                .artist(artist)
                .requestPostId(post.getRequestPostId())
                .applicationId(applicationId)
                .agreedPrice(application.getProposedPrice() != null
                        ? application.getProposedPrice()
                        : (post.getBudgetMin() != null ? post.getBudgetMin() : java.math.BigDecimal.ZERO))
                .agreedDeadline(post.getDeadline())
                .build();

        commissionRepository.save(commission);
        return commission.getCommissionId();
    }

    // 지원 취소 (작가, PENDING 상태만)
    @Transactional
    public void cancel(Long artistId, Long applicationId) {
        CommissionApplication application = findById(applicationId);

        if (!application.getArtist().getUserId().equals(artistId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        if (!"PENDING".equals(application.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        applicationRepository.delete(application);
    }

    private CommissionApplication findById(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private CommissionApplication findByIdWithLock(Long applicationId) {
        return applicationRepository.findByIdWithLock(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
    }
}
