package com.expansion.server.domain.asset.service;

import com.expansion.server.domain.asset.dto.*;
import com.expansion.server.domain.asset.entity.*;
import com.expansion.server.domain.asset.repository.*;
import com.expansion.server.domain.common.entity.Like;
import com.expansion.server.domain.common.entity.Tag;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetImageRepository assetImageRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final AssetPurchaseRepository assetPurchaseRepository;
    private final AssetCommentRepository assetCommentRepository;
    private final AssetTagRepository assetTagRepository;
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

        return AssetResponse.of(asset, profile, imageUrls, tags, false, false, request.getFileUrl());
    }

    public AssetResponse getAsset(Long assetId, Long currentUserId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));

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

        // 다운로드 파일 URL — 무료이거나 구매한 경우에만 노출
        boolean canDownload = asset.isFree() || asset.getPrice().signum() == 0 || isPurchased;
        String fileUrl = canDownload
                ? assetVersionRepository.findByAsset_AssetIdAndIsCurrentTrue(assetId)
                    .map(AssetVersion::getFileUrl).orElse(null)
                : null;

        return AssetResponse.of(asset, profile, imageUrls, tags, isLiked, isPurchased, fileUrl);
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
                null, null
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

        return AssetResponse.of(asset, profile, imageUrls, tags, isLiked, isPurchased, fileUrl);
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

    public Page<AssetSummary> getAssetList(Boolean isFree, Pageable pageable) {
        Page<Asset> assets;
        if (isFree != null) {
            assets = assetRepository.findByStatusAndIsFree("ACTIVE", isFree, pageable);
        } else {
            assets = assetRepository.findByStatus("ACTIVE", pageable);
        }
        return toSummaryPage(assets);
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

        if (assetPurchaseRepository.existsByUser_UserIdAndAsset_AssetId(userId, assetId)) {
            throw new CustomException(ErrorCode.ALREADY_PURCHASED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        assetPurchaseRepository.save(AssetPurchase.builder()
                .user(user).asset(asset).build());

        asset.incrementDownloadCount();
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

        AssetComment comment = AssetComment.builder()
                .asset(asset).user(user).parent(parent).content(request.getContent())
                .build();

        assetCommentRepository.save(comment);
        asset.incrementCommentCount();

        // 에셋 소유자에게 댓글 알림 (본인 댓글은 NotificationService에서 제외)
        eventPublisher.publishEvent(NotificationEvent.of(
                asset.getUser().getUserId(), userId, NotificationType.ASSET_COMMENT, assetId));

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        return AssetCommentResponse.of(comment, profile);
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
