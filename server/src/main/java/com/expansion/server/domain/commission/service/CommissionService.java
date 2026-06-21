package com.expansion.server.domain.commission.service;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.commission.entity.CommissionFile;
import com.expansion.server.domain.commission.entity.CommissionPreviewImage;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    private static final long MAX_PREVIEW_BYTES = 10L * 1024 * 1024;   // 미리보기 이미지 1장 10MB 상한
    private static final int MAX_PREVIEW_COUNT = 10;                   // 커미션당 미리보기 최대 장수

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
            // 전이 무결성: 검토 요청은 진행 중에서만 (COMPLETED→REVIEW 등 역전이 차단)
            if (!"IN_PROGRESS".equals(commission.getStatus())) {
                throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
            }
            // 검토 요청 전 납품물(작가 업로드 ≥1)과 미리보기(1장 이상) 둘 다 있어야 함
            Long artistId = commission.getArtist().getUserId();
            boolean hasDelivery = commission.getFiles().stream()
                    .anyMatch(f -> f.getUploader().getUserId().equals(artistId));
            if (!hasDelivery || commission.getPreviewImages().isEmpty()) {
                throw new CustomException(ErrorCode.DELIVERY_REQUIRED);
            }
        } else if ("COMPLETED".equals(target)) {
            if (!isClient) throw new CustomException(ErrorCode.ACCESS_DENIED);
            // 완료 확정은 검토 단계에서만
            if (!"REVIEW".equals(commission.getStatus())) {
                throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
            }
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
        commission.getFiles().add(file);   // 응답이 방금 올린 파일을 즉시 포함하도록 컬렉션에 반영

        Profile clientProfile = profileRepository.findByUser_UserId(commission.getClient().getUserId()).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(commission.getArtist().getUserId()).orElse(null);

        return CommissionResponse.of(commission, clientProfile, artistProfile, uploaderId);
    }

    /**
     * 작가가 납품 파일 1개 삭제. R2 객체 + DB 행 제거. 완료/취소된 계약은 불가.
     */
    @Transactional
    public CommissionResponse deleteDeliveryFile(Long uploaderId, Long commissionId, Long fileId) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        if (!commission.getArtist().getUserId().equals(uploaderId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);   // 납품 파일은 작가만
        }
        if ("COMPLETED".equals(commission.getStatus()) || "CANCELLED".equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);   // 종료된 계약은 변경 불가
        }

        CommissionFile target = commission.getFiles().stream()
                .filter(f -> f.getFileId().equals(fileId)
                        && f.getUploader().getUserId().equals(uploaderId))   // 작가 본인 파일만
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));   // 이 계약의 작가 납품 파일 아님

        // DB 제거를 먼저(트랜잭션 일관성) → R2는 나중. R2 실패 시 스토리지 고아만 남고(비노출) 사용자엔 영향 없음.
        String fileUrlToDelete = target.getFileUrl();
        commission.getFiles().remove(target);   // orphanRemoval → DB 삭제

        if (r2Uploader != null) {
            try {
                r2Uploader.delete(fileUrlToDelete);   // 스토리지 정리
            } catch (Exception e) {
                // 스토리지 삭제 실패해도 DB 행은 이미 제거됨 (고아 객체는 추후 정리)
                log.warn("R2 delivery file delete failed. commissionId={}, fileId={}", commissionId, fileId, e);
            }
        }

        Profile clientProfile = profileRepository.findByUser_UserId(commission.getClient().getUserId()).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(commission.getArtist().getUserId()).orElse(null);
        return CommissionResponse.of(commission, clientProfile, artistProfile, uploaderId);
    }

    /**
     * 작가가 검토용 미리보기 이미지를 여러 장 업로드 → 서버가 각각 워터마크+축소 후 R2 저장, 행 append.
     * 원본 납품물(fileUrl)과 별개. 의뢰자는 이 미리보기들로만 검토(완료 전 원본 마스킹).
     */
    @Transactional
    public CommissionResponse uploadPreviews(Long uploaderId, Long commissionId, List<MultipartFile> images) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        if (!commission.getArtist().getUserId().equals(uploaderId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);   // 미리보기는 작가만
        }
        // 입력 검증을 R2 가용성 체크보다 먼저 — 잘못된 요청은 기능 비활성(503)과 무관하게 400.
        // (워터마킹/업로드 전에 거르므로 500 방지 + R2 off인 로컬에서도 400 검증 가능)
        if (images == null || images.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        // 장수 상한 — 기존 + 신규가 최대치를 넘으면 거절
        if (commission.getPreviewImages().size() + images.size() > MAX_PREVIEW_COUNT) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new CustomException(ErrorCode.INVALID_INPUT);   // 이미지만 허용
            }
            if (image.getSize() > MAX_PREVIEW_BYTES) {
                throw new CustomException(ErrorCode.INVALID_INPUT);   // 크기 초과
            }
        }
        if (r2Uploader == null) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_DISABLED);   // 로컬 R2 off (입력은 정상)
        }

        try {
            for (MultipartFile image : images) {
                byte[] watermarked = watermarkService.watermarkPreview(image, commissionId);
                String url = r2Uploader.uploadBytes(
                        watermarked, "image/jpeg", ".jpg", "commissions/" + commissionId + "/preview");
                commission.addPreviewImage(url);
            }
        } catch (IOException e) {
            // 디코딩/이미지 처리 실패는 손상/비이미지 입력 → 클라이언트 오류(400)
            throw new CustomException(ErrorCode.INVALID_INPUT, e);
        }

        Profile clientProfile = profileRepository.findByUser_UserId(commission.getClient().getUserId()).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(commission.getArtist().getUserId()).orElse(null);
        return CommissionResponse.of(commission, clientProfile, artistProfile, uploaderId);
    }

    /**
     * 작가가 검토용 미리보기 이미지 1장을 삭제. R2 객체 + DB 행 제거.
     */
    @Transactional
    public CommissionResponse deletePreview(Long uploaderId, Long commissionId, Long previewImageId) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        if (!commission.getArtist().getUserId().equals(uploaderId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);   // 미리보기는 작가만
        }

        CommissionPreviewImage target = commission.getPreviewImages().stream()
                .filter(p -> p.getPreviewImageId().equals(previewImageId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));   // 이 커미션의 미리보기가 아님

        if (r2Uploader != null) {
            try {
                r2Uploader.delete(target.getImageUrl());   // 스토리지 정리
            } catch (Exception e) {
                // 스토리지 삭제 실패해도 DB 행은 제거 (고아 객체는 추후 정리 대상) — 주석 의도대로 계속 진행
                log.warn("R2 preview delete failed. commissionId={}, previewImageId={}",
                        commissionId, previewImageId, e);
            }
        }
        commission.removePreviewImage(target);

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
