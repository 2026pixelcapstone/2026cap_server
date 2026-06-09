package com.expansion.server.domain.gallery.service;

import com.expansion.server.domain.common.entity.Like;
import com.expansion.server.domain.common.entity.Tag;
import com.expansion.server.domain.common.repository.CategoryRepository;
import com.expansion.server.domain.common.repository.LikeRepository;
import com.expansion.server.domain.common.repository.TagRepository;
import com.expansion.server.domain.gallery.dto.*;
import com.expansion.server.domain.gallery.entity.*;
import com.expansion.server.domain.gallery.repository.*;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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
public class GalleryService {

    private final GalleryPostRepository galleryPostRepository;
    private final GalleryImageRepository galleryImageRepository;
    private final GalleryCommentRepository galleryCommentRepository;
    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;
    private final LikeRepository likeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    private static final String TARGET_TYPE = "GALLERY_POST";

    // ──────────────────────────────────────────────
    // 게시물 CRUD
    // ──────────────────────────────────────────────

    @Transactional
    public GalleryPostResponse createPost(Long userId, GalleryPostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        // 소프트 게이트 — 이메일 미인증 사용자는 작품 등록 불가
        if (!user.isEmailVerified()) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        GalleryPost originPost = null;
        if (request.getOriginPostId() != null) {
            originPost = galleryPostRepository.findById(request.getOriginPostId())
                    .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
            originPost.incrementRemixCount();
        }

        var category = request.getCategoryId() != null
                ? categoryRepository.findById(request.getCategoryId()).orElse(null)
                : null;

        GalleryPost post = GalleryPost.builder()
                .user(user)
                .category(category)
                .title(request.getTitle())
                .description(request.getDescription())
                .thumbnailUrl(request.getThumbnailUrl())
                .galleryType(GalleryType.valueOf(request.getGalleryType()))
                .visibility(request.getVisibility() != null
                        ? Visibility.valueOf(request.getVisibility()) : Visibility.PUBLIC)
                .isEditable(request.isEditable())
                .isCollaborative(request.isCollaborative())
                .originPost(originPost)
                .build();

        galleryPostRepository.save(post);

        // 이미지 저장
        saveImages(post, request.getImageUrls());

        // 태그 저장
        List<String> tags = saveTags(post, request.getTags());

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        List<String> imageUrls = request.getImageUrls() != null ? request.getImageUrls() : List.of();

        return GalleryPostResponse.of(post, profile, imageUrls, tags, false);
    }

    public GalleryPostResponse getPost(Long postId, Long currentUserId) {
        GalleryPost post = galleryPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        // 비공개 게시물 접근 제한
        if (post.getVisibility() == Visibility.PRIVATE
                && !post.getUser().getUserId().equals(currentUserId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        Profile profile = profileRepository.findByUser_UserId(post.getUser().getUserId()).orElse(null);

        List<String> imageUrls = galleryImageRepository
                .findByPost_PostIdOrderBySortOrderAsc(postId)
                .stream()
                .map(GalleryImage::getImageUrl)
                .toList();

        List<String> tags = postTagRepository.findByPost_PostId(postId)
                .stream()
                .map(pt -> pt.getTag().getTagName())
                .toList();

        boolean isLiked = currentUserId != null
                && likeRepository.existsByUser_UserIdAndTargetIdAndTargetType(
                currentUserId, postId, TARGET_TYPE);

        return GalleryPostResponse.of(post, profile, imageUrls, tags, isLiked);
    }

    /** 조회수만 증가 — POST /view 전용. 데이터 조회 없이 단순 카운트 증가만 수행 */
    @Transactional
    public void incrementView(Long postId) {
        GalleryPost post = galleryPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        post.incrementViewCount();
    }

    @Transactional
    public GalleryPostResponse getPostAndIncrementView(Long postId, Long currentUserId) {
        GalleryPost post = galleryPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (post.getVisibility() == Visibility.PRIVATE
                && !post.getUser().getUserId().equals(currentUserId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        post.incrementViewCount();

        Profile profile = profileRepository.findByUser_UserId(post.getUser().getUserId()).orElse(null);

        List<String> imageUrls = galleryImageRepository
                .findByPost_PostIdOrderBySortOrderAsc(postId)
                .stream()
                .map(GalleryImage::getImageUrl)
                .toList();

        List<String> tags = postTagRepository.findByPost_PostId(postId)
                .stream()
                .map(pt -> pt.getTag().getTagName())
                .toList();

        boolean isLiked = currentUserId != null
                && likeRepository.existsByUser_UserIdAndTargetIdAndTargetType(
                currentUserId, postId, TARGET_TYPE);

        return GalleryPostResponse.of(post, profile, imageUrls, tags, isLiked);
    }

    @Transactional
    public GalleryPostResponse updatePost(Long userId, Long postId, GalleryPostUpdateRequest request) {
        GalleryPost post = galleryPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        var category = request.getCategoryId() != null
                ? categoryRepository.findById(request.getCategoryId()).orElse(null)
                : post.getCategory();

        Visibility visibility = request.getVisibility() != null
                ? Visibility.valueOf(request.getVisibility()) : null;

        boolean isEditable = request.getIsEditable() != null ? request.getIsEditable() : post.isEditable();

        post.update(request.getTitle(), request.getDescription(),
                request.getThumbnailUrl(), visibility, isEditable, category);

        // 이미지 교체
        if (request.getImageUrls() != null) {
            galleryImageRepository.deleteByPost_PostId(postId);
            saveImages(post, request.getImageUrls());
        }

        // 태그 교체
        List<String> tags;
        if (request.getTags() != null) {
            // 기존 태그 카운트 감소
            postTagRepository.findByPost_PostId(postId).forEach(pt -> pt.getTag().decreasePostCount());
            postTagRepository.deleteByPost_PostId(postId);
            tags = saveTags(post, request.getTags());
        } else {
            tags = postTagRepository.findByPost_PostId(postId)
                    .stream().map(pt -> pt.getTag().getTagName()).toList();
        }

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        List<String> imageUrls = galleryImageRepository
                .findByPost_PostIdOrderBySortOrderAsc(postId)
                .stream().map(GalleryImage::getImageUrl).toList();

        boolean isLiked = likeRepository.existsByUser_UserIdAndTargetIdAndTargetType(
                userId, postId, TARGET_TYPE);

        return GalleryPostResponse.of(post, profile, imageUrls, tags, isLiked);
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        GalleryPost post = galleryPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        if (!post.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        // 태그 카운트 감소 (PostTag는 cascade로 삭제되므로 카운트만 먼저 처리)
        postTagRepository.findByPost_PostId(postId)
                .forEach(pt -> pt.getTag().decreasePostCount());

        // 댓글은 cascade 설정이 없으므로 FK 위반 방지를 위해 먼저 삭제
        galleryCommentRepository.deleteByPost_PostId(postId);

        galleryPostRepository.delete(post);
    }

    // ──────────────────────────────────────────────
    // 목록 조회
    // ──────────────────────────────────────────────

    public Page<GalleryPostSummary> getPostList(String galleryType, Pageable pageable) {
        GalleryType type = GalleryType.valueOf(galleryType);
        Page<GalleryPost> posts = galleryPostRepository
                .findByVisibilityAndGalleryType(Visibility.PUBLIC, type, pageable);

        return toSummaryPage(posts);
    }

    public Page<GalleryPostSummary> getUserPosts(Long targetUserId, Long currentUserId, Pageable pageable) {
        Page<GalleryPost> posts;
        if (targetUserId.equals(currentUserId)) {
            posts = galleryPostRepository.findByUser_UserId(targetUserId, pageable);
        } else {
            posts = galleryPostRepository.findByUser_UserIdAndVisibility(
                    targetUserId, Visibility.PUBLIC, pageable);
        }
        return toSummaryPage(posts);
    }

    public Page<GalleryPostSummary> searchPosts(String keyword, Pageable pageable) {
        return toSummaryPage(galleryPostRepository.searchByKeyword(keyword, pageable));
    }

    public Page<GalleryPostSummary> getPostsByTag(String tagName, String type, Pageable pageable) {
        GalleryType galleryType = (type != null) ? GalleryType.valueOf(type) : null;
        return toSummaryPage(galleryPostRepository.findByTagName(tagName, galleryType, pageable));
    }

    // 유저가 좋아요한 게시물 목록 (본인 포함 타인도 PUBLIC만 노출)
    public Page<GalleryPostSummary> getLikedPosts(Long userId, Pageable pageable) {
        return toSummaryPage(
                galleryPostRepository.findLikedByUser(userId, Visibility.PUBLIC, pageable));
    }

    // 여러 작가의 대표작(포트폴리오) 일괄 조회 — authorId → 최신 PUBLIC top-N summary
    // 카드 목록의 카드별 N+1을 한 번의 배치 호출로 대체하기 위한 용도
    public Map<Long, List<GalleryPostSummary>> getPortfolios(List<Long> authorIds, int perAuthor) {
        if (authorIds == null || authorIds.isEmpty()) return Map.of();
        int limit = Math.max(1, Math.min(perAuthor, 12)); // 1~12 상한

        List<GalleryPost> posts = galleryPostRepository.findTopNByAuthors(authorIds, limit);
        if (posts.isEmpty()) return Map.of();

        SummaryMaps maps = loadSummaryMaps(posts);

        // authorId로 그룹핑 (쿼리에서 user_id, rn 순 정렬 → 작가별 최신순 유지)
        return posts.stream().collect(Collectors.groupingBy(
                p -> p.getUser().getUserId(),
                Collectors.mapping(p -> GalleryPostSummary.of(
                                p,
                                maps.profileMap().get(p.getUser().getUserId()),
                                maps.tagMap().getOrDefault(p.getPostId(), List.of())),
                        Collectors.toList())
        ));
    }

    // ──────────────────────────────────────────────
    // 좋아요
    // ──────────────────────────────────────────────

    @Transactional
    public boolean toggleLike(Long userId, Long postId) {
        GalleryPost post = galleryPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        var existing = likeRepository
                .findByUser_UserIdAndTargetIdAndTargetType(userId, postId, TARGET_TYPE);

        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            post.decrementLikeCount();
            return false;
        } else {
            likeRepository.save(Like.builder()
                    .user(user)
                    .targetId(postId)
                    .targetType(TARGET_TYPE)
                    .build());
            post.incrementLikeCount();
            return true;
        }
    }

    // ──────────────────────────────────────────────
    // 댓글
    // ──────────────────────────────────────────────

    @Transactional
    public GalleryCommentResponse createComment(Long userId, Long postId,
                                                GalleryCommentCreateRequest request) {
        GalleryPost post = galleryPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        GalleryComment parent = null;
        if (request.getParentId() != null) {
            parent = galleryCommentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        }

        GalleryComment comment = GalleryComment.builder()
                .post(post)
                .user(user)
                .parent(parent)
                .content(request.getContent())
                .build();

        galleryCommentRepository.save(comment);
        post.incrementCommentCount();

        Profile profile = profileRepository.findByUser_UserId(userId).orElse(null);
        return GalleryCommentResponse.of(comment, profile);
    }

    public Page<GalleryCommentResponse> getComments(Long postId, Pageable pageable) {
        Page<GalleryComment> comments = galleryCommentRepository
                .findByPost_PostIdAndParentIsNull(postId, pageable);

        // 댓글 작성자 프로필을 일괄 조회
        List<Long> userIds = comments.stream()
                .map(c -> c.getUser().getUserId())
                .distinct()
                .toList();

        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        return comments.map(c ->
                GalleryCommentResponse.of(c, profileMap.get(c.getUser().getUserId())));
    }

    @Transactional
    public void deleteComment(Long userId, Long postId, Long commentId) {
        GalleryComment comment = galleryCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        // 댓글이 요청한 게시글에 속하는지 검증
        if (!comment.getPost().getPostId().equals(postId)) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        comment.softDelete();
        comment.getPost().decrementCommentCount();
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────

    private void saveImages(GalleryPost post, List<String> imageUrls) {
        if (imageUrls == null) return;
        for (int i = 0; i < imageUrls.size(); i++) {
            galleryImageRepository.save(GalleryImage.builder()
                    .post(post)
                    .imageUrl(imageUrls.get(i))
                    .sortOrder(i)
                    .build());
        }
    }

    private List<String> saveTags(GalleryPost post, List<String> tagNames) {
        if (tagNames == null) return List.of();
        for (String name : tagNames) {
            Tag tag = tagRepository.findByTagName(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().tagName(name).build()));
            tag.increasePostCount();
            postTagRepository.save(PostTag.builder().post(post).tag(tag).build());
        }
        return tagNames;
    }

    private Page<GalleryPostSummary> toSummaryPage(Page<GalleryPost> posts) {
        SummaryMaps maps = loadSummaryMaps(posts.getContent());
        return posts.map(p -> GalleryPostSummary.of(
                p,
                maps.profileMap().get(p.getUser().getUserId()),
                maps.tagMap().getOrDefault(p.getPostId(), List.of())
        ));
    }

    // 요약 DTO 조립용 프로필/태그 맵 묶음
    private record SummaryMaps(Map<Long, Profile> profileMap, Map<Long, List<String>> tagMap) {}

    // 게시물 목록의 작성자 프로필·태그를 각각 한 번의 쿼리로 일괄 조회 (N+1 방지, 공용)
    private SummaryMaps loadSummaryMaps(List<GalleryPost> posts) {
        List<Long> userIds = posts.stream()
                .map(p -> p.getUser().getUserId())
                .distinct()
                .toList();

        Map<Long, Profile> profileMap = profileRepository.findAllByUser_UserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), p -> p));

        List<Long> postIds = posts.stream().map(GalleryPost::getPostId).toList();
        Map<Long, List<String>> tagMap = postTagRepository.findByPost_PostIdIn(postIds)
                .stream()
                .collect(Collectors.groupingBy(
                        pt -> pt.getPost().getPostId(),
                        Collectors.mapping(pt -> pt.getTag().getTagName(), Collectors.toList())
                ));

        return new SummaryMaps(profileMap, tagMap);
    }
}
