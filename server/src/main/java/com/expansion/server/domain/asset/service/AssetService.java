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

        // 다운로드 파일 저장 (asset_versions v1.0)
        if (request.getFileUrl() != null && !request.getFileUrl().isBlank()) {
            AssetVersion version = AssetVersion.builder()
                    .asset(asset)
                    .versionNumber(1)
                    .versionName("v1.0")
                    .fileUrl(request.getFileUrl())
                    .fileSize(request.getFileSize())
                    .isCurrent(true)
                    .build();
            assetVersionRepository.save(version);
        }

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        List<String> imageUrls = request.getImageUrls() != null ? request.getImageUrls() : List.of();

        return AssetResponse.of(asset, profile, imageUrls, tags, false, false, request.getFileUrl(), null);
    }

    @Transactional
    public AssetResponse getAsset(Long assetId, Long currentUserId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        assetRepository.incrementViewCount(assetId);   // 상세 조회 시 조회수 원자적 증가

        Profile profile = profileRepository.findByUser_UserId(asset.getUser().getUserId()).orElse(null);

        List<String> imageUrls = assetImageRepository
                .findByAsset_AssetIdOrderBySortOrderAsc(assetId)
                .stream().map(AssetImage::getImageUrl).toList();

        List<String> tags = assetTagRepository.findByAsset_AssetId(assetId)
                .stream().map(at -> at.getTag().getTagName()).toList();

        boolean isLiked = currentUserId != null
                && likeRepository.existsByUser_UserIdAndTargetIdAndTargetType(currentUserId, assetId, TARGET_TYPE);

        boolean isPurchased = currentUserId != null
                && assetPurchaseRepository.existsByUser_UserIdAndAsset_AssetId(currentUserId, assetId);

        // 다운로드 파일 URL — 로그인 + (무료이거나 구매)한 경우에만 노출 (비로그인은 다운로드 불가)
        boolean canDownload = currentUserId != null
                && (asset.isFree() || asset.getPrice().signum() == 0 || isPurchased);
        String fileUrl = canDownload
                ? assetVersionRepository.findByAsset_AssetIdAndIsCurrentTrue(assetId)
                    .map(AssetVersion::getFileUrl).orElse(null)
                : null;

        // 현재 유저가 남긴 별점(있으면)
        Integer myRating = currentUserId == null ? null
                : assetCommentRepository
                    .findFirstByAsset_AssetIdAndUser_UserIdAndRatingIsNotNullAndIsDeletedFalse(assetId, currentUserId)
                    .map(AssetComment::getRating).orElse(null);

        return AssetResponse.of(asset, profile, imageUrls, tags, isLiked, isPurchased, fileUrl, myRating);
    }

    @Transactional
    public AssetResponse updateAsset(Long userId, Long assetId, AssetUpdateRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        if (!asset.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        asset.update(
                request.getTitle(),
                request.getDescription(),
                request.getThumbnailUrl(),
                request.getPrice(),
                request.getIsFree() != null ? request.getIsFree() : asset.isFree(),
                request.getCategoryId() != null ? resolveCategory(request.getCategoryId()) : asset.getCategory(),
                request.getLicenseTypeId() != null ? resolveLicenseType(request.getLicenseTypeId()) : asset.getLicenseType()
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
        boolean isPurchased = assetPurchaseRepository
                .existsByUser_UserIdAndAsset_AssetId(userId, assetId);

        String fileUrl = assetVersionRepository.findByAsset_AssetIdAndIsCurrentTrue(assetId)
                .map(AssetVersion::getFileUrl).orElse(null);

        // 작성자 본인 수정 화면 — 본인은 평가 대상 아님(myRating null)
        return AssetResponse.of(asset, profile, imageUrls, tags, isLiked, isPurchased, fileUrl, null);
    }

    @Transactional
    public void deleteAsset(Long userId, Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        if (!asset.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        assetTagRepository.findByAsset_AssetId(assetId)
                .forEach(at -> at.getTag().decreasePostCount());

        // asset_versions는 cascade가 없으므로 명시적으로 삭제
        assetVersionRepository.deleteByAsset_AssetId(assetId);

        assetRepository.delete(asset);
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
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private AssetLicenseType resolveLicenseType(Long licenseTypeId) {
        if (licenseTypeId == null) return null;
        return assetLicenseTypeRepository.findById(licenseTypeId)
                .orElseThrow(() -> new CustomException(ErrorCode.LICENSE_TYPE_NOT_FOUND));
    }

    public Page<AssetSummary> getUserAssets(Long userId, Pageable pageable) {
        return toSummaryPage(assetRepository.findByUser_UserId(userId, pageable));
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

    @Transactional
    public void purchaseAsset(Long userId, Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        // 무료 에셋은 구매 대상이 아님 (바로 다운로드)
        if (asset.isFree() || asset.getPrice().signum() == 0) {
            throw new CustomException(ErrorCode.CANNOT_PURCHASE_FREE_ASSET);
        }

        if (assetPurchaseRepository.existsByUser_UserIdAndAsset_AssetId(userId, assetId)) {
            throw new CustomException(ErrorCode.ALREADY_PURCHASED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        assetPurchaseRepository.save(AssetPurchase.builder()
                .user(user).asset(asset).build());
        // 구매 ≠ 다운로드 — downloadCount는 실제 다운로드(recordDownload)에서만 증가
    }

    // ──────────────────────────────────────────────
    // 다운로드 (로그인 필수, 사람×에셋 중복 제거 카운트)
    // ──────────────────────────────────────────────

    @Transactional
    public void recordDownload(Long userId, Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

        // 유료 에셋은 구매자만 다운로드 가능 (무료는 로그인만으로 OK)
        boolean isFreeAsset = asset.isFree() || asset.getPrice().signum() == 0;
        if (!isFreeAsset && !assetPurchaseRepository.existsByUser_UserIdAndAsset_AssetId(userId, assetId)) {
            throw new CustomException(ErrorCode.DOWNLOAD_NOT_ALLOWED);
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
            boolean acquired = asset.isFree() || asset.getPrice().signum() == 0
                    || assetPurchaseRepository.existsByUser_UserIdAndAsset_AssetId(userId, assetId);
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
