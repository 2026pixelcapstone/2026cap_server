package com.expansion.server.domain.commission.service;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.commission.entity.CommissionFile;
import com.expansion.server.domain.chat.service.ChatService;
import com.expansion.server.domain.commission.repository.CommissionFileRepository;
import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.domain.notification.entity.NotificationType;
import com.expansion.server.domain.notification.event.NotificationEvent;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.expansion.server.global.util.R2Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionService {

    private final CommissionRepository commissionRepository;
    private final CommissionFileRepository commissionFileRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ChatService chatService;
    private final ApplicationEventPublisher eventPublisher;
    private final WatermarkService watermarkService;

    // R2는 r2.enabled=true(서버)일 때만 빈이 존재 → 로컬에선 null
    @Autowired(required = false)
    private R2Uploader r2Uploader;

    @Transactional
    public CommissionResponse createCommission(Long clientId, CommissionCreateRequest request) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        User artist = userRepository.findById(request.getArtistId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Commission commission = Commission.builder()
                .commissionType(request.getCommissionType())
                .client(client)
                .artist(artist)
                .serviceId(request.getServiceId())
                .requestPostId(request.getRequestPostId())
                .applicationId(request.getApplicationId())
                .agreedPrice(request.getAgreedPrice())
                .agreedDeadline(request.getAgreedDeadline())
                .status("IN_PROGRESS")
                .build();

        commissionRepository.save(commission);

        Profile clientProfile = profileRepository.findByUser_UserId(clientId).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(artist.getUserId()).orElse(null);

        return CommissionResponse.of(commission, clientProfile, artistProfile, clientId);
    }

    public CommissionResponse getCommission(Long commissionId, Long currentUserId) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        boolean isClient = commission.getClient().getUserId().equals(currentUserId);
        boolean isArtist = commission.getArtist().getUserId().equals(currentUserId);

        if (!isClient && !isArtist) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Profile clientProfile = profileRepository.findByUser_UserId(commission.getClient().getUserId()).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(commission.getArtist().getUserId()).orElse(null);

        return CommissionResponse.of(commission, clientProfile, artistProfile, currentUserId);
    }

    public Page<CommissionSummary> getMyCommissionsAsClient(Long userId, Pageable pageable) {
        Page<Commission> page = commissionRepository.findByClient_UserId(userId, pageable);
        return toSummaryWithUnread(page, userId);
    }

    public Page<CommissionSummary> getMyCommissionsAsArtist(Long userId, Pageable pageable) {
        Page<Commission> page = commissionRepository.findByArtist_UserId(userId, pageable);
        return toSummaryWithUnread(page, userId);
    }

    // 커미션 목록 → 요약 + (프로필·안읽음 수를 각각 배치 조회해 임베드, N+1 방지)
    private Page<CommissionSummary> toSummaryWithUnread(Page<Commission> page, Long userId) {
        List<Long> commissionIds = page.getContent().stream()
                .map(Commission::getCommissionId).toList();

        // 클라이언트/작가 프로필을 한 번에 일괄 조회
        List<Long> userIds = page.getContent().stream()
                .flatMap(c -> Stream.of(c.getClient().getUserId(), c.getArtist().getUserId()))
                .distinct()
                .toList();
        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        Map<Long, Long> unread = chatService.getUnreadCounts(commissionIds, userId);

        return page.map(c -> CommissionSummary.of(c,
                profileMap.get(c.getClient().getUserId()),
                profileMap.get(c.getArtist().getUserId()),
                unread.getOrDefault(c.getCommissionId(), 0L)));
    }

    @Transactional
    public CommissionResponse updateStatus(Long userId, Long commissionId, CommissionUpdateRequest request) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        boolean isClient = commission.getClient().getUserId().equals(userId);
        boolean isArtist = commission.getArtist().getUserId().equals(userId);

        if (!isClient && !isArtist) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // 문서 기준 흐름: 작가가 "검토 요청"(REVIEW), 의뢰자가 "완료 확정"(COMPLETED)
        String target = request.getStatus();
        if ("REVIEW".equals(target)) {
            if (!isArtist) throw new CustomException(ErrorCode.ACCESS_DENIED);
            // 검토 요청 전 납품물(원본)과 미리보기 둘 다 있어야 함 — 의뢰자는 미리보기로 검토
            if (commission.getFileUrl() == null || commission.getPreviewUrl() == null) {
                throw new CustomException(ErrorCode.DELIVERY_REQUIRED);
            }
        } else if ("COMPLETED".equals(target)) {
            if (!isClient) throw new CustomException(ErrorCode.ACCESS_DENIED);
        } else {
            // IN_PROGRESS/CANCELLED 등 직접 전환은 이 엔드포인트에서 허용하지 않음 (취소는 /cancel 사용)
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        commission.updateStatus(target);

        // 상대방에게 상태 변경 알림 (작가→검토요청은 의뢰자에게, 의뢰자→완료확정은 작가에게)
        if ("REVIEW".equals(target)) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    commission.getClient().getUserId(), userId,
                    NotificationType.COMMISSION_REVIEW, commissionId));
        } else if ("COMPLETED".equals(target)) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    commission.getArtist().getUserId(), userId,
                    NotificationType.COMMISSION_COMPLETED, commissionId));
        }

        Profile clientProfile = profileRepository.findByUser_UserId(commission.getClient().getUserId()).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(commission.getArtist().getUserId()).orElse(null);

        return CommissionResponse.of(commission, clientProfile, artistProfile, userId);
    }

    @Transactional
    public CommissionResponse uploadFile(Long uploaderId, Long commissionId,
                                         String fileType, String fileUrl,
                                         String fileName, Long fileSize) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        boolean isClient = commission.getClient().getUserId().equals(uploaderId);
        boolean isArtist = commission.getArtist().getUserId().equals(uploaderId);

        if (!isClient && !isArtist) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        CommissionFile file = CommissionFile.builder()
                .commission(commission)
                .uploader(uploader)
                .fileType(fileType)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .fileSize(fileSize)
                .isPublic(false)
                .build();

        commissionFileRepository.save(file);

        // 작가가 올린 최신 파일을 커미션 대표 납품 파일(fileUrl)로 노출
        // (의뢰자가 참고자료를 올려도 납품 링크가 덮이지 않도록 작가 업로드 시에만 갱신)
        if (isArtist) {
            commission.setFileUrl(fileUrl);
        }

        Profile clientProfile = profileRepository.findByUser_UserId(commission.getClient().getUserId()).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(commission.getArtist().getUserId()).orElse(null);

        return CommissionResponse.of(commission, clientProfile, artistProfile, uploaderId);
    }

    /**
     * 작가가 검토용 미리보기 이미지를 업로드 → 서버가 워터마크+축소 후 R2 저장, previewUrl 세팅.
     * 원본 납품물(fileUrl)과 별개. 의뢰자는 이 previewUrl로만 검토(완료 전 원본 마스킹).
     */
    @Transactional
    public CommissionResponse uploadPreview(Long uploaderId, Long commissionId, MultipartFile image) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        if (!commission.getArtist().getUserId().equals(uploaderId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);   // 미리보기는 작가만
        }
        if (r2Uploader == null) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_DISABLED);   // 로컬 R2 off
        }
        if (image == null || image.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        try {
            byte[] watermarked = watermarkService.watermarkPreview(image, commissionId);
            String url = r2Uploader.uploadBytes(
                    watermarked, "image/jpeg", ".jpg", "commissions/" + commissionId + "/preview");
            commission.setPreviewUrl(url);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }

        Profile clientProfile = profileRepository.findByUser_UserId(commission.getClient().getUserId()).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(commission.getArtist().getUserId()).orElse(null);
        return CommissionResponse.of(commission, clientProfile, artistProfile, uploaderId);
    }

    @Transactional
    public void cancelCommission(Long userId, Long commissionId) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        boolean isClient = commission.getClient().getUserId().equals(userId);
        boolean isArtist = commission.getArtist().getUserId().equals(userId);

        if (!isClient && !isArtist) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        if ("COMPLETED".equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        commission.cancel();

        // 취소한 사람의 상대방에게 알림
        Long recipientId = isClient ? commission.getArtist().getUserId()
                                    : commission.getClient().getUserId();
        eventPublisher.publishEvent(NotificationEvent.of(
                recipientId, userId, NotificationType.COMMISSION_CANCELLED, commissionId));
    }
}
