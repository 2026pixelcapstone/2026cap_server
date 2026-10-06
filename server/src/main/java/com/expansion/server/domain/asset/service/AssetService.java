package com.expansion.server.domain.asset.service;

import com.expansion.server.domain.asset.dto.*;
import com.expansion.server.domain.asset.entity.*;
import com.expansion.server.domain.asset.repository.*;
import com.expansion.server.domain.common.entity.Category;
import com.expansion.server.domain.common.entity.Like;
import com.expansion.server.domain.common.entity.Tag;
import com.expansion.server.domain.common.repository.CategoryRepository;
import com.expansion.server.domain.common.repository.LikeRepository;
import com.expansion.server.domain.common.repository.TagRepository;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.service.EmailVerificationGuard;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import com.expansion.server.domain.notification.entity.NotificationType;
import com.expansion.server.domain.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetImageRepository assetImageRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final AssetPurchaseRepository assetPurchaseRepository;
    private final AssetDownloadRepository assetDownloadRepository;
    private final AssetCommentRepository assetCommentRepository;
    private final AssetTagRepository assetTagRepository;
    private final AssetLicenseTypeRepository assetLicenseTypeRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final LikeRepository likeRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;
    // R2Uploader는 @ConditionalOnBean(S3Client) — r2.enabled 일 때만 빈 존재. 옵셔널 주입.
    private final org.springframework.beans.factory.ObjectProvider<com.expansion.server.global.util.R2Uploader> r2UploaderProvider;

    private static final String TARGET_TYPE = "ASSET";

    // ──────────────────────────────────────────────
    // 에셋 CRUD
    // ──────────────────────────────────────────────

    @Transactional
    public AssetResponse createAsset(Long userId, AssetCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        EmailVerificationGuard.assertVerified(user);   // 소프트 게이트 — 미인증 시 에셋 업로드 불가

        Asset asset = Asset.builder()
                .user(user)
                .category(resolveCategory(request.getCategoryId()))
                .licenseType(resolveLicenseType(request.getLicenseTypeId()))
                .title(request.getTitle())
                .description(request.getDescription())
                .thumbnailUrl(request.getThumbnailUrl())
                .price(request.getPrice())
                .isFree(request.isFree())
                .build();

        assetRepository.save(asset);

        saveImages(asset, request.getImageUrls());
        List<String> tags = saveTags(asset, request.getTags());

        // 다운로드 파일 저장 (첫 파일 — 이후 수정에서 여러 개 추가/삭제 가능)
        if (request.getFileUrl() != null && !request.getFileUrl().isBlank()) {
            AssetVersion version = AssetVersion.builder()
                    .asset(asset)
                    .versionNumber(1)
                    .versionName("v1")
                    .fileUrl(request.getFileUrl())
                    .fileName(request.getFileName())
                    .fileSize(request.getFileSize())
                    .isCurrent(true)
                    .build();
            assetVersionRepository.save(version);
        }

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        List<String> imageUrls = request.getImageUrls() != null ? request.getImageUrls() : List.of();

        return AssetResponse.of(asset, profile, imageUrls, tags, false, false,
                buildDownloadFiles(asset.getAssetId(), true), null);
    }

    // ──────────────────────────────────────────────
    // 다운로드 파일 (멀티 파일 — 추가/삭제/목록)
    // ──────────────────────────────────────────────

    /** 다운로드 파일 1개 추가(작성자만). 여러 파일이 공존 — 교체 아님. */
    @Transactional
    public AssetVersionResponse addVersion(Long userId, Long assetId, AssetVersionCreateRequest request) {
        // 에셋 행 비관적 락 — 동시 추가가 같은 nextNumber를 읽어 (asset_id, version_number) 유니크 위반 방지
        Asset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));
        if (!asset.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        requireActive(asset);

        int nextNumber = assetVersionRepository
                .findFirstByAsset_AssetIdOrderByVersionNumberDesc(assetId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        AssetVersion version = AssetVersion.builder()
                .asset(asset)
                .versionNumber(nextNumber)
                .versionName("v" + nextNumber)   // 내부 유니크 라벨(UI 미노출)
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .changeNote(request.getChangeNote())
                .isCurrent(true)                 // 멀티 파일 — 모두 활성
                .build();
        assetVersionRepository.save(version);

        return AssetVersionResponse.of(version);
    }

    /** 다운로드 파일 1개 삭제(작성자만) — DB 행 + R2 파일 삭제. */
    @Transactional
    public void deleteVersion(Long userId, Long assetId, Long versionId) {
        Asset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));
        if (!asset.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        requireActive(asset);   // 판매 중지 후엔 소유자가 받을 파일이므로 작성자도 지울 수 없음
        AssetVersion version = assetVersionRepository.findById(versionId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));
        if (!version.getAsset().getAssetId().equals(assetId)) {
            throw new CustomException(ErrorCode.ASSET_NOT_FOUND);   // 해당 에셋 소속 아님
        }

        String fileUrl = version.getFileUrl();
        assetVersionRepository.delete(version);

        // R2 파일 정리는 DB 커밋 이후에 — 트랜잭션 롤백 시 파일만 사라지는 불일치 방지. best-effort.
        scheduleR2DeleteAfterCommit(fileUrl);
    }

    /** R2 파일 삭제를 트랜잭션 커밋 이후로 예약(롤백 시 삭제 안 함). 트랜잭션 밖이면 즉시 삭제. */
    private void scheduleR2DeleteAfterCommit(String fileUrl) {
        if (fileUrl == null) return;
        com.expansion.server.global.util.R2Uploader r2 = r2UploaderProvider.getIfAvailable();
        if (r2 == null) return;

        Runnable deleteTask = () -> {
            try {
                r2.delete(fileUrl);
            } catch (RuntimeException e) {
                log.warn("[Asset] 다운로드 파일 R2 삭제 실패(무시) — url={}, err={}", fileUrl, e.getMessage());
            }
        };

        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { deleteTask.run(); }
                    });
        } else {
            deleteTask.run();
        }
    }

    /** 다운로드 파일 목록 조회(작성자만) — 관리 UI용. fileUrl은 노출하지 않음(id·이름·크기만). */
    public List<AssetVersionResponse> getVersions(Long userId, Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));
        if (!asset.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        return assetVersionRepository.findByAsset_AssetIdOrderByCreatedAtDesc(assetId)
                .stream().map(AssetVersionResponse::of).toList();
    }

    /** 상세 응답용 다운로드 파일 목록 — includeUrl=false면 fileUrl 마스킹(파일 존재·이름·크기만). */
    private List<AssetDownloadFileResponse> buildDownloadFiles(Long assetId, boolean includeUrl) {
        return assetVersionRepository.findByAsset_AssetIdOrderByCreatedAtDesc(assetId).stream()
                .map(v -> new AssetDownloadFileResponse(
                        v.getFileName(), v.getFileSize(), includeUrl ? v.getFileUrl() : null))
                .toList();
    }

    @Transactional
    public AssetResponse getAsset(Long assetId, Long currentUserId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        boolean isAuthor = currentUserId != null && asset.getUser().getUserId().equals(currentUserId);
        boolean isPurchased = owns(currentUserId, assetId);   // 유료 구매 또는 무료 취득(ACTIVE)

        // 판매 중지된 에셋은 작성자·소유자만 볼 수 있음(그 외엔 없는 에셋처럼 404)
        if (!asset.isActive() && !isAuthor && !isPurchased) {
            throw new CustomException(ErrorCode.ASSET_NOT_FOUND);
        }

        assetRepository.incrementViewCount(assetId);   // 상세 조회 시 조회수 원자적 증가

        Profile profile = profileRepository.findByUser_UserId(asset.getUser().getUserId()).orElse(null);

        List<String> imageUrls = assetImageRepository
                .findByAsset_AssetIdOrderBySortOrderAsc(assetId)
                .stream().map(AssetImage::getImageUrl).toList();

        List<String> tags = assetTagRepository.findByAsset_AssetId(assetId)
                .stream().map(at -> at.getTag().getTagName()).toList();

        boolean isLiked = currentUserId != null
                && likeRepository.existsByUser_UserIdAndTargetIdAndTargetType(currentUserId, assetId, TARGET_TYPE);

        // 다운로드 파일 목록 — fileUrl은 로그인 + (소유했거나, 판매 중인 무료 에셋)일 때만 노출(비로그인은 다운로드 불가).
        // 파일 존재·이름·크기는 누구나 볼 수 있음(구매 전 'N개 파일 포함' 표시). recordDownload 판정과 같은 규칙.
        boolean canDownload = currentUserId != null
                && (isPurchased || (asset.isActive() && asset.isEffectivelyFree()));
        List<AssetDownloadFileResponse> downloadFiles = buildDownloadFiles(assetId, canDownload);

        // 현재 유저가 남긴 별점(있으면)
        Integer myRating = currentUserId == null ? null
                : assetCommentRepository
                    .findFirstByAsset_AssetIdAndUser_UserIdAndRatingIsNotNullAndIsDeletedFalse(assetId, currentUserId)
                    .map(AssetComment::getRating).orElse(null);

        return AssetResponse.of(asset, profile, imageUrls, tags, isLiked, isPurchased, downloadFiles, myRating);
    }

    @Transactional
    public AssetResponse updateAsset(Long userId, Long assetId, AssetUpdateRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        if (!asset.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        requireActive(asset);

        // 카테고리/라이선스는 항상 요청값으로 덮어씀 — null이면 해제(수정 폼은 항상 현재값을 전송).
        asset.update(
                request.getTitle(),
                request.getDescription(),
                request.getThumbnailUrl(),
                request.getPrice(),
                request.getIsFree() != null ? request.getIsFree() : asset.isFree(),
                resolveCategory(request.getCategoryId()),
                resolveLicenseType(request.getLicenseTypeId())
        );

        if (request.getImageUrls() != null) {
            assetImageRepository.deleteByAsset_AssetId(assetId);
            saveImages(asset, request.getImageUrls());
        }

        List<String> tags;
        if (request.getTags() != null) {
            assetTagRepository.findByAsset_AssetId(assetId)
                    .forEach(at -> at.getTag().decreasePostCount());
            assetTagRepository.deleteByAsset_AssetId(assetId);
            tags = saveTags(asset, request.getTags());
        } else {
            tags = assetTagRepository.findByAsset_AssetId(assetId)
                    .stream().map(at -> at.getTag().getTagName()).toList();
        }

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        List<String> imageUrls = assetImageRepository
                .findByAsset_AssetIdOrderBySortOrderAsc(assetId)
                .stream().map(AssetImage::getImageUrl).toList();

        boolean isLiked = likeRepository
                .existsByUser_UserIdAndTargetIdAndTargetType(userId, assetId, TARGET_TYPE);
        boolean isPurchased = owns(userId, assetId);

        // 작성자 본인 수정 화면 — 본인은 평가 대상 아님(myRating null). 다운로드 파일은 본인이라 URL 포함.
        return AssetResponse.of(asset, profile, imageUrls, tags, isLiked, isPurchased,
                buildDownloadFiles(assetId, true), null);
    }

    /**
     * 에셋 삭제. 결제 이력이 있으면 행을 지우지 않고 '판매 중지'(DELETED)로 남긴다 —
     * 목록·검색에서 빠지지만 소유자는 계속 다운로드(Unity·Gumroad·itch 방식), 결제·환불 이력도 보존.
     * 결제 이력이 없으면(무료 취득만 있거나 아무도 안 받음) 기존처럼 완전 삭제 — 무료로 받은 사람 목록에서도 사라짐.
     * 결과를 알려주기 위해 판매 중지면 true.
     */
    @Transactional
    public boolean deleteAsset(Long userId, Long assetId) {
        // 에셋 행 락 — 삭제 판정 중 결제 승인(confirmAsset)·무료 취득(recordDownload)이 끼어들지 않게 직렬화
        Asset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        if (!asset.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (!asset.isActive()) return true;   // 이미 판매 중지 — 멱등(태그 카운트 중복 감소 방지)

        assetTagRepository.findByAsset_AssetId(assetId)
                .forEach(at -> at.getTag().decreasePostCount());

        if (assetPurchaseRepository.existsByAsset_AssetIdAndPaymentIdIsNotNull(assetId)) {
            asset.discontinue();
            return true;
        }

        // 완전 삭제 — asset_purchases(무료 취득 행)·asset_versions는 cascade가 없으므로 명시적으로 삭제
        assetPurchaseRepository.deleteFreeAcquisitions(assetId);
        assetVersionRepository.deleteByAsset_AssetId(assetId);

        assetRepository.delete(asset);
        return false;
    }

    // ──────────────────────────────────────────────
    // 목록 조회
    // ──────────────────────────────────────────────

    public Page<AssetSummary> getAssetList(Boolean isFree, Long categoryId, Pageable pageable) {
        return toSummaryPage(assetRepository.findActiveAssets(categoryId, isFree, pageable));
    }

    // 에셋 카테고리/라이선스 선택지 (업로드 드롭다운·필터)
    public List<CategoryResponse> getAssetCategories() {
        return categoryRepository.findByTypeOrderBySortOrderAsc("ASSET")
                .stream().map(CategoryResponse::of).toList();
    }

    public List<AssetLicenseTypeResponse> getLicenseTypes() {
        return assetLicenseTypeRepository.findAllByOrderByLicenseTypeIdAsc()
                .stream().map(AssetLicenseTypeResponse::of).toList();
    }

    // categoryId/licenseTypeId → 엔티티 (없는 id면 400, null이면 null)
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        // 에셋엔 ASSET 타입 카테고리만 — GALLERY 등 다른 타입 연결 방지
        if (!"ASSET".equals(category.getType())) {
            throw new CustomException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private AssetLicenseType resolveLicenseType(Long licenseTypeId) {
        if (licenseTypeId == null) return null;
        return assetLicenseTypeRepository.findById(licenseTypeId)
                .orElseThrow(() -> new CustomException(ErrorCode.LICENSE_TYPE_NOT_FOUND));
    }

    public Page<AssetSummary> getUserAssets(Long userId, Pageable pageable) {
        return toSummaryPage(assetRepository.findByUser_UserIdAndStatus(userId, Asset.STATUS_ACTIVE, pageable));
    }

    /** 마이페이지 '구매/받은 에셋' — paid=true면 유료 구매, false면 무료 취득. 판매 중지 에셋도 포함(소유자는 계속 다운로드). */
    public Page<LibraryAssetResponse> getLibrary(Long userId, boolean paid, Pageable pageable) {
        Page<AssetPurchase> page = paid
                ? assetPurchaseRepository.findByUser_UserIdAndStatusAndPaymentIdIsNotNull(userId, AssetPurchase.STATUS_ACTIVE, pageable)
                : assetPurchaseRepository.findByUser_UserIdAndStatusAndPaymentIdIsNull(userId, AssetPurchase.STATUS_ACTIVE, pageable);

        List<Long> authorIds = page.stream()
                .map(p -> p.getAsset().getUser().getUserId()).distinct().toList();
        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(authorIds)
                .stream().collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        return page.map(p -> LibraryAssetResponse.of(p, profileMap.get(p.getAsset().getUser().getUserId())));
    }

    public Page<AssetSummary> searchAssets(String keyword, Pageable pageable) {
        return toSummaryPage(assetRepository.searchByKeyword(keyword, pageable));
    }

    public Page<AssetSummary> getAssetsByTag(String tagName, Boolean isFree, Pageable pageable) {
        return toSummaryPage(assetRepository.findByTagName(tagName, isFree, pageable));
    }

    // ──────────────────────────────────────────────
    // 좋아요
    // ──────────────────────────────────────────────

    @Transactional
    public boolean toggleLike(Long userId, Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));
        requireActive(asset);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        var existing = likeRepository
                .findByUser_UserIdAndTargetIdAndTargetType(userId, assetId, TARGET_TYPE);

        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            asset.decrementLikeCount();
            return false;
        } else {
            likeRepository.save(Like.builder()
                    .user(user).targetId(assetId).targetType(TARGET_TYPE).build());
            asset.incrementLikeCount();
            return true;
        }
    }

    // ──────────────────────────────────────────────
    // 구매
    // ──────────────────────────────────────────────

    // 유료 에셋 구매는 결제(payment 도메인 confirmAsset)에서 AssetPurchase를 생성한다.
    // 결제 없이 구매를 기록하던 purchaseAsset()은 무료 취득 구멍이라 제거됨.

    // ──────────────────────────────────────────────
    // 다운로드 (로그인 필수, 사람×에셋 중복 제거 카운트)
    // ──────────────────────────────────────────────

    @Transactional
    public void recordDownload(Long userId, Long assetId) {
        // 에셋 행 락 — 무료 취득 기록과 deleteAsset(완전 삭제) 판정이 엇갈리지 않게 직렬화
        Asset asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        // 소유자(유료 구매·무료 취득)는 판매 중지 후에도 다운로드 가능.
        // 소유하지 않았으면 '판매 중인 무료 에셋'만 가능 — 받는 순간 무료 취득 행을 남겨,
        // 나중에 유료로 바뀌어도 무료일 때 받은 사람의 권리가 유지되게 한다(작성자 본인은 기록 안 함).
        if (!owns(userId, assetId)) {
            if (!asset.isActive()) {
                throw new CustomException(ErrorCode.ASSET_DISCONTINUED);
            }
            if (!asset.isEffectivelyFree()) {
                throw new CustomException(ErrorCode.DOWNLOAD_NOT_ALLOWED);
            }
            if (!asset.getUser().getUserId().equals(userId)) {
                assetPurchaseRepository.insertFreeAcquisitionIfAbsent(userId, assetId);
            }
        }

        // ON CONFLICT DO NOTHING으로 원자적 삽입 → 처음 받는 사용자(1행 삽입)일 때만 카운트 증가.
        // 동시 다운로드 race도 DB가 직렬화하므로 별도 예외 처리 불필요.
        if (assetDownloadRepository.insertIfAbsent(userId, assetId) > 0) {
            assetRepository.incrementDownloadCount(assetId);
        }
    }

    // ──────────────────────────────────────────────
    // 댓글
    // ──────────────────────────────────────────────

    @Transactional
    public AssetCommentResponse createComment(Long userId, Long assetId,
                                              AssetCommentCreateRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));
        requireActive(asset);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        AssetComment parent = null;
        if (request.getParentId() != null) {
            parent = assetCommentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        }

        // 별점은 최상위 리뷰에만 유효 — 대댓글이면 무시
        Integer rating = (parent == null) ? request.getRating() : null;

        if (rating != null) {
            // 게이팅: 작성자 본인 불가 + (무료거나 구매자만)
            // isFree 플래그와 price==0을 모두 확인하는 건 의도적 — 둘이 불일치하는 데이터(예: isFree=false인데 price 0)에도
            // "사실상 무료"는 취득자로 보아 평가를 허용하기 위한 방어적 체크.
            boolean isAuthor = asset.getUser().getUserId().equals(userId);
            boolean acquired = asset.isEffectivelyFree() || owns(userId, assetId);
            if (isAuthor || !acquired) {
                throw new CustomException(ErrorCode.RATING_NOT_ALLOWED);
            }

            // 유저당 1리뷰 — 기존 리뷰가 있으면 갱신(재등록)
            Optional<AssetComment> existing = assetCommentRepository
                    .findFirstByAsset_AssetIdAndUser_UserIdAndRatingIsNotNullAndIsDeletedFalse(assetId, userId);
            if (existing.isPresent()) {
                AssetComment review = existing.get();
                review.updateReview(request.getContent(), rating);
                recomputeRating(asset);
                Profile p = profileRepository.findByUser_UserId(userId).orElse(null);
                return AssetCommentResponse.of(review, p);
            }
        }

        AssetComment comment = AssetComment.builder()
                .asset(asset).user(user).parent(parent).content(request.getContent()).rating(rating)
                .build();

        assetCommentRepository.save(comment);
        asset.incrementCommentCount();
        if (rating != null) recomputeRating(asset);

        // 에셋 소유자에게 댓글 알림 (본인 댓글은 NotificationService에서 제외)
        eventPublisher.publishEvent(NotificationEvent.of(
                asset.getUser().getUserId(), userId, NotificationType.ASSET_COMMENT, assetId));

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        return AssetCommentResponse.of(comment, profile);
    }

    // 별점 요약(분포) — 상세 페이지 평점 영역
    public AssetRatingSummaryResponse getRatingSummary(Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        long[] dist = new long[6]; // index 0은 미사용, 1~5가 별점 값과 직접 매핑되어 가독성 향상
        for (Object[] row : assetCommentRepository.ratingDistribution(assetId)) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            int star = ((Number) row[0]).intValue();
            long cnt = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            if (star >= 1 && star <= 5) dist[star] = cnt;
        }
        List<Long> distribution = List.of(dist[5], dist[4], dist[3], dist[2], dist[1]);
        return new AssetRatingSummaryResponse(asset.getAverageRating(), asset.getReviewCount(), distribution);
    }

    // 별점 집계 재계산 — 리뷰 생성/수정/삭제 후 호출
    private void recomputeRating(Asset asset) {
        List<Object[]> rows = assetCommentRepository.aggregateRating(asset.getAssetId());
        Object[] agg = rows.isEmpty() ? null : rows.get(0);
        Double avg = (agg != null && agg[0] != null) ? ((Number) agg[0]).doubleValue() : null;
        long count = (agg != null && agg[1] != null) ? ((Number) agg[1]).longValue() : 0L;
        BigDecimal average = avg != null
                ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        asset.applyRatingStats(average, (int) count);
    }

    public Page<AssetCommentResponse> getComments(Long assetId, Pageable pageable) {
        var comments = assetCommentRepository
                .findByAsset_AssetIdAndParentIsNull(assetId, pageable);

        List<Long> userIds = comments.stream()
                .map(c -> c.getUser().getUserId()).distinct().toList();

        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(userIds)
                .stream().collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        return comments.map(c -> AssetCommentResponse.of(c, profileMap.get(c.getUser().getUserId())));
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        AssetComment comment = assetCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        comment.softDelete();
        comment.getAsset().decrementCommentCount();

        // 별점 있던 리뷰가 삭제되면 집계 재계산
        if (comment.getRating() != null) {
            recomputeRating(comment.getAsset());
        }
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────

    /** 소유 여부 — ACTIVE 소유권 행(유료 구매·무료 취득). 환불(REFUNDED)은 권리 없음. 비로그인은 false. */
    private boolean owns(Long userId, Long assetId) {
        return userId != null && assetPurchaseRepository.existsActive(userId, assetId);
    }

    /** 판매 중지된 에셋에 대한 쓰기(수정·파일 변경·좋아요·댓글) 차단 */
    private static void requireActive(Asset asset) {
        if (!asset.isActive()) {
            throw new CustomException(ErrorCode.ASSET_DISCONTINUED);
        }
    }

    private void saveImages(Asset asset, List<String> imageUrls) {
        if (imageUrls == null) return;
        for (int i = 0; i < imageUrls.size(); i++) {
            assetImageRepository.save(AssetImage.builder()
                    .asset(asset).imageUrl(imageUrls.get(i)).sortOrder(i).build());
        }
    }

    private List<String> saveTags(Asset asset, List<String> tagNames) {
        if (tagNames == null) return List.of();
        for (String name : tagNames) {
            Tag tag = tagRepository.findByTagName(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().tagName(name).build()));
            tag.increasePostCount();
            assetTagRepository.save(AssetTag.builder().asset(asset).tag(tag).build());
        }
        return tagNames;
    }

    private Page<AssetSummary> toSummaryPage(Page<Asset> assets) {
        List<Long> userIds = assets.stream()
                .map(a -> a.getUser().getUserId()).distinct().toList();

        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(userIds)
                .stream().collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        List<Long> assetIds = assets.stream().map(Asset::getAssetId).toList();
        Map<Long, List<String>> tagMap = assetTagRepository.findByAsset_AssetIdIn(assetIds)
                .stream()
                .collect(Collectors.groupingBy(
                        at -> at.getAsset().getAssetId(),
                        Collectors.mapping(at -> at.getTag().getTagName(), Collectors.toList())
                ));

        return assets.map(a -> AssetSummary.of(
                a,
                profileMap.get(a.getUser().getUserId()),
                tagMap.getOrDefault(a.getAssetId(), List.of())
        ));
    }
}
