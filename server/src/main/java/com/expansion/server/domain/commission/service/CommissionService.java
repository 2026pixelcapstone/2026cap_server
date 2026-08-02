package com.expansion.server.domain.commission.service;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.commission.entity.CommissionFile;
import com.expansion.server.domain.commission.entity.CommissionPreviewImage;
import com.expansion.server.domain.commission.entity.ArtistService;
import com.expansion.server.domain.commission.entity.RequestPost;
import com.expansion.server.domain.chat.service.ChatService;
import com.expansion.server.domain.commission.repository.ArtistServiceRepository;
import com.expansion.server.domain.commission.repository.CommissionFileRepository;
import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.domain.commission.repository.RequestPostRepository;
import com.expansion.server.domain.notification.entity.NotificationType;
import com.expansion.server.domain.notification.event.NotificationEvent;
import com.expansion.server.domain.payment.service.PaymentService;
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
import java.util.ArrayList;
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
    private final ArtistServiceRepository artistServiceRepository;
    private final RequestPostRepository requestPostRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ChatService chatService;
    private final ApplicationEventPublisher eventPublisher;
    private final WatermarkService watermarkService;
    private final PaymentService paymentService;   // 에스크로: 완료 시 지급(RELEASED)·취소 시 환불(REFUNDED)

    // R2는 r2.enabled=true(서버)일 때만 빈이 존재 → 로컬에선 null
    @Autowired(required = false)
    private R2Uploader r2Uploader;

    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;   // 업로드 파일 1개 10MB 상한
    private static final int MAX_UPLOAD_FILES_PER_REQUEST = 5;        // 요청당 파일 수 상한(트랜잭션 내 외부 I/O 억제)
    private static final int MAX_PREVIEW_COUNT = 10;                  // 커미션당 미리보기 최대 장수(자동 생성 상한)

    @Transactional
    public CommissionResponse createCommission(Long clientId, CommissionCreateRequest request) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        User artist = userRepository.findById(request.getArtistId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 거래 기록 스냅샷 — 원본(작가서비스/의뢰글)에서 제목·내용 복사(원글 수정·삭제돼도 거래엔 당시 정보 보존)
        // commissionType과 원본 ID를 같은 규칙으로 검증: REQUEST=의뢰글만, SERVICE_*=작가서비스만.
        // (타입과 안 맞는 ID 조합·두 ID 공존을 막아 불일치 커미션 생성 방지) + 원본 못 찾으면 fail-fast.
        boolean hasServiceId = request.getServiceId() != null;
        boolean hasRequestPostId = request.getRequestPostId() != null;
        String snapshotTitle;
        String snapshotDescription;

        if ("REQUEST".equals(request.getCommissionType())) {
            if (!hasRequestPostId || hasServiceId) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            RequestPost post = requestPostRepository.findById(request.getRequestPostId())
                    .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
            snapshotTitle = post.getTitle();
            snapshotDescription = post.getDescription();
        } else {   // SERVICE_OPTION / SERVICE_QUOTE
            if (!hasServiceId || hasRequestPostId) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            ArtistService service = artistServiceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> new CustomException(ErrorCode.ARTIST_SERVICE_NOT_FOUND));
            snapshotTitle = service.getTitle();
            snapshotDescription = service.getDescription();
        }

        Commission commission = Commission.builder()
                .commissionType(request.getCommissionType())
                .client(client)
                .artist(artist)
                .serviceId(request.getServiceId())
                .requestPostId(request.getRequestPostId())
                .applicationId(request.getApplicationId())
                .agreedPrice(request.getAgreedPrice())
                .agreedDeadline(request.getAgreedDeadline())
                // 에스크로: 유료 계약은 결제 대기(PENDING_PAYMENT)로 시작 → 의뢰자 결제 후 IN_PROGRESS.
                // 금액이 없거나 0인 계약(무료/협의 전)은 결제 없이 바로 작업 시작.
                .status(request.getAgreedPrice() != null && request.getAgreedPrice().signum() > 0
                        ? "PENDING_PAYMENT" : "IN_PROGRESS")
                .title(snapshotTitle)
                .description(snapshotDescription)
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

    // 진행 중(IN_PROGRESS/REVIEW) 거래 전체 — 양쪽 역할 합쳐 서버에서 상태 필터.
    // "거래룸 상시 진입점"(네비/배너/메인 카드)용. 페이지네이션의 첫 20건에 밀려 활성 거래가 누락되는 문제 해결.
    public List<CommissionSummary> getMyActiveCommissions(Long userId) {
        List<Commission> list = commissionRepository.findActiveByUser(userId, ACTIVE_STATUSES);
        Map<Long, Profile> profileMap = loadProfiles(list);
        Map<Long, Long> unread = chatService.getUnreadCounts(
                list.stream().map(Commission::getCommissionId).toList(), userId);
        return list.stream()
                .map(c -> CommissionSummary.of(c,
                        profileMap.get(c.getClient().getUserId()),
                        profileMap.get(c.getArtist().getUserId()),
                        unread.getOrDefault(c.getCommissionId(), 0L)))
                .toList();
    }

    // 거래룸 상시 진입점(네비/배너/메인)에 노출할 "활성" 상태. 결제 대기도 포함 —
    // 의뢰자가 결제하러 거래룸에 들어와야 하므로.
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING_PAYMENT", "IN_PROGRESS", "REVIEW");

    // 커미션 목록 → 요약 + (프로필·안읽음 수를 각각 배치 조회해 임베드, N+1 방지)
    private Page<CommissionSummary> toSummaryWithUnread(Page<Commission> page, Long userId) {
        List<Long> commissionIds = page.getContent().stream()
                .map(Commission::getCommissionId).toList();
        Map<Long, Profile> profileMap = loadProfiles(page.getContent());
        Map<Long, Long> unread = chatService.getUnreadCounts(commissionIds, userId);

        return page.map(c -> CommissionSummary.of(c,
                profileMap.get(c.getClient().getUserId()),
                profileMap.get(c.getArtist().getUserId()),
                unread.getOrDefault(c.getCommissionId(), 0L)));
    }

    // 커미션 목록의 당사자(의뢰자·작가) 프로필을 한 번에 일괄 조회 (userId → Profile). N+1 방지.
    private Map<Long, Profile> loadProfiles(List<Commission> commissions) {
        List<Long> userIds = commissions.stream()
                .flatMap(c -> Stream.of(c.getClient().getUserId(), c.getArtist().getUserId()))
                .distinct()
                .toList();
        return profileRepository.findAllByUser_UserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));
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
            // 검토 요청 전 납품물(작가 업로드 ≥1) 필요.
            // 미리보기는 원본 업로드 시 자동 생성되므로 별도 필수 조건에서 제외
            // (psd 등 비이미지만 납품하는 경우 허용 — 검토는 채팅/동반 이미지로 보완하는 정책)
            Long artistId = commission.getArtist().getUserId();
            boolean hasDelivery = commission.getFiles().stream()
                    .anyMatch(f -> f.getUploader().getUserId().equals(artistId));
            if (!hasDelivery) {
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

        // 완료 확정 → 보관 중이던 결제(HELD)를 작가 지급 예정(RELEASED)으로 전환
        if ("COMPLETED".equals(target)) {
            paymentService.releaseForCommission(commission.getPaymentId());
        }

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

    /**
     * 납품/참고 파일 업로드 — "원본 = 미리보기" 재설계 (multipart, 서버 경유).
     *
     * <p>작가가 원본만 올리면 서버가 파일 타입별로 검토용 미리보기를 자동 처리한다:
     * <ul>
     *   <li>정적 이미지(png/jpg 등) → 워터마크 미리보기 자동 생성</li>
     *   <li>gif → 첫 프레임 워터마크(ImageIO가 첫 프레임만 디코딩) — 프론트가 "GIF 애니메이션" 라벨 표시</li>
     *   <li>webp(디코딩 미지원)·psd 등 비이미지 → 미리보기 없음(완료 후 다운로드만)</li>
     * </ul>
     * 미리보기 생성 실패는 업로드 실패로 번지지 않는다(원본 저장이 우선, 미리보기는 베스트 에포트).
     * 의뢰자 업로드(참고자료)는 미리보기를 만들지 않는다 — 에스크로 검토 대상은 작가 납품물뿐.
     */
    @Transactional
    public CommissionResponse uploadDeliveryFiles(Long uploaderId, Long commissionId,
                                                  List<MultipartFile> files, String fileType) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        boolean isClient = commission.getClient().getUserId().equals(uploaderId);
        boolean isArtist = commission.getArtist().getUserId().equals(uploaderId);
        if (!isClient && !isArtist) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if ("COMPLETED".equals(commission.getStatus()) || "CANCELLED".equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);   // 종료된 계약엔 업로드 불가
        }

        // 입력 검증을 R2 가용성 체크보다 먼저(잘못된 요청은 400, R2 off 로컬에서도 검증 가능)
        if (files == null || files.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (files.size() > MAX_UPLOAD_FILES_PER_REQUEST) {
            throw new CustomException(ErrorCode.INVALID_INPUT);   // 요청당 파일 수 초과
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            if (file.getSize() > MAX_UPLOAD_BYTES) {
                throw new CustomException(ErrorCode.FILE_TOO_LARGE);   // 파일당 크기 초과(413)
            }
        }
        if (r2Uploader == null) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_DISABLED);   // 로컬 R2 off
        }

        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 중간 실패 시 이미 R2에 올라간 객체를 보상 삭제하기 위한 추적 목록.
        // (DB insert는 @Transactional 롤백이 정리하지만 R2엔 트랜잭션이 없어 고아 객체가 남음)
        List<String> uploadedUrls = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String originalName = file.getOriginalFilename();
                String extension = extractExtension(originalName);
                String contentType = file.getContentType() != null
                        ? file.getContentType() : "application/octet-stream";

                // 1) 원본을 R2에 저장
                String fileUrl;
                try {
                    fileUrl = r2Uploader.uploadBytes(file.getBytes(), contentType, extension,
                            "commissions/" + commissionId + "/files");
                } catch (IOException e) {
                    throw new CustomException(ErrorCode.INVALID_INPUT, e);   // 요청 바디 읽기 실패
                }
                uploadedUrls.add(fileUrl);

                CommissionFile saved = CommissionFile.builder()
                        .commission(commission)
                        .uploader(uploader)
                        .fileType(fileType)
                        .fileUrl(fileUrl)
                        .fileName(originalName)
                        .fileSize(file.getSize())
                        .isPublic(false)
                        .build();
                commissionFileRepository.save(saved);          // IDENTITY → 즉시 insert, fileId 확보
                commission.getFiles().add(saved);              // 응답 즉시 반영

                // 2) 작가 납품 이미지면 워터마크 미리보기 자동 생성 (베스트 에포트 — 실패해도 원본은 유지)
                if (isArtist && contentType.startsWith("image/")
                        && commission.getPreviewImages().size() < MAX_PREVIEW_COUNT) {
                    try {
                        byte[] watermarked = watermarkService.watermarkPreview(file, commissionId);
                        String previewUrl = r2Uploader.uploadBytes(
                                watermarked, "image/jpeg", ".jpg", "commissions/" + commissionId + "/preview");
                        uploadedUrls.add(previewUrl);
                        commission.addPreviewImage(previewUrl, saved.getFileId());
                    } catch (Exception e) {
                        // webp 등 디코딩 미지원/손상 이미지 → 미리보기 없이 원본만(프론트가 타입 라벨 표시)
                        log.info("preview auto-generation skipped. commissionId={}, file={}, cause={}",
                                commissionId, originalName, e.toString());
                    }
                }
            }
        } catch (RuntimeException e) {
            // 중간 실패 → DB는 롤백되지만 R2엔 이미 올라간 객체가 남으므로 보상 삭제(베스트 에포트)
            for (String url : uploadedUrls) {
                try {
                    r2Uploader.delete(url);
                } catch (Exception cleanupError) {
                    log.warn("R2 compensation cleanup failed. commissionId={}, url={}", commissionId, url, cleanupError);
                }
            }
            throw e;
        }

        Profile clientProfile = profileRepository.findByUser_UserId(commission.getClient().getUserId()).orElse(null);
        Profile artistProfile = profileRepository.findByUser_UserId(commission.getArtist().getUserId()).orElse(null);
        return CommissionResponse.of(commission, clientProfile, artistProfile, uploaderId);
    }

    /** 파일명에서 확장자 추출(".png" 형태). 없으면 빈 문자열. */
    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0 && dot < fileName.length() - 1) ? fileName.substring(dot) : "";
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

        // 이 파일에서 자동 생성된 미리보기도 함께 제거(원본=미리보기 연동. DB엔 CASCADE도 있으나
        // in-memory 컬렉션 일관성 + R2 객체 정리를 위해 코드에서도 명시 제거)
        List<CommissionPreviewImage> linkedPreviews = commission.getPreviewImages().stream()
                .filter(p -> fileId.equals(p.getSourceFileId()))
                .toList();

        // DB 제거를 먼저(트랜잭션 일관성) → R2는 나중. R2 실패 시 스토리지 고아만 남고(비노출) 사용자엔 영향 없음.
        String fileUrlToDelete = target.getFileUrl();
        List<String> previewUrlsToDelete = linkedPreviews.stream()
                .map(CommissionPreviewImage::getImageUrl)
                .toList();
        // 🔴 미리보기 DELETE를 먼저 flush로 확정해야 함 — 파일 행을 먼저 지우면 DB의
        //    ON DELETE CASCADE가 미리보기를 이미 지워버려, Hibernate의 미리보기 DELETE가
        //    0행 갱신(ObjectOptimisticLockingFailureException → 500)으로 터진다(실발생).
        commission.getPreviewImages().removeAll(linkedPreviews);   // orphanRemoval → DB 삭제
        commissionRepository.flush();                              // 미리보기 삭제 즉시 반영
        commission.getFiles().remove(target);                      // orphanRemoval → DB 삭제(CASCADE 대상 없음)
        commissionRepository.flush();                              // 파일 DELETE도 R2 삭제 전에 확정(커밋 실패 시 스토리지만 지워지는 것 방지)

        if (r2Uploader != null) {
            try {
                r2Uploader.delete(fileUrlToDelete);   // 원본 스토리지 정리
                for (String previewUrl : previewUrlsToDelete) {
                    r2Uploader.delete(previewUrl);    // 연동 미리보기 스토리지 정리
                }
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
     * (제거됨) 미리보기 별도 업로드/삭제 — "원본 = 미리보기" 재설계로 폐지.
     * 미리보기는 uploadDeliveryFiles에서 자동 생성되고, 원본 파일 삭제 시 연동 삭제된다.
     */

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

        // 실제 취소 전이가 일어난 경우에만 알림(이미 취소된 계약 재호출 시 중복 알림 방지)
        if (!commission.cancel()) {
            return;
        }

        // 보관 중(HELD)인 결제가 있으면 환불(REFUNDED). RELEASED(완료 후) 환불은 별도 정책 — 후속.
        paymentService.refundForCommission(commission.getPaymentId(), "커미션 취소");

        // 취소한 사람의 상대방에게 알림
        Long recipientId = isClient ? commission.getArtist().getUserId()
                                    : commission.getClient().getUserId();
        eventPublisher.publishEvent(NotificationEvent.of(
                recipientId, userId, NotificationType.COMMISSION_CANCELLED, commissionId));
    }
}
