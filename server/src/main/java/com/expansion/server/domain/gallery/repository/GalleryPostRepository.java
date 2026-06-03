package com.expansion.server.domain.gallery.repository;

import com.expansion.server.domain.gallery.entity.GalleryPost;
import com.expansion.server.domain.gallery.entity.GalleryType;
import com.expansion.server.domain.gallery.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GalleryPostRepository extends JpaRepository<GalleryPost, Long> {

    // 여러 작가의 PUBLIC 최신 게시물 top-N을 한 쿼리로 (포트폴리오 배치 조회, N+1 방지)
    // ROW_NUMBER() 윈도우 함수로 작가별 파티션 후 perAuthor개까지만 추출
    @Query(value = """
            SELECT sub.* FROM (
                SELECT p.*, ROW_NUMBER() OVER (
                    PARTITION BY p.user_id ORDER BY p.created_at DESC
                ) AS rn
                FROM gallery_posts p
                WHERE p.user_id IN (:authorIds) AND p.visibility = 'PUBLIC'
            ) sub
            WHERE sub.rn <= :perAuthor
            ORDER BY sub.user_id, sub.rn
            """, nativeQuery = true)
    List<GalleryPost> findTopNByAuthors(@Param("authorIds") List<Long> authorIds,
                                        @Param("perAuthor") int perAuthor);

    // 공개 게시물 목록 (타입별)
    Page<GalleryPost> findByVisibilityAndGalleryType(Visibility visibility, GalleryType galleryType, Pageable pageable);

    // 특정 유저의 게시물 (본인 조회 시 전체, 타인 조회 시 PUBLIC만)
    Page<GalleryPost> findByUser_UserIdAndVisibility(Long userId, Visibility visibility, Pageable pageable);

    Page<GalleryPost> findByUser_UserId(Long userId, Pageable pageable);

    // 카테고리별 공개 게시물
    Page<GalleryPost> findByCategory_CategoryIdAndVisibility(Long categoryId, Visibility visibility, Pageable pageable);

    // 태그로 게시물 검색 (galleryType 선택 필터)
    @Query("""
            SELECT DISTINCT p FROM GalleryPost p
            JOIN p.postTags pt
            JOIN pt.tag t
            WHERE t.tagName = :tagName
            AND p.visibility = 'PUBLIC'
            AND (:galleryType IS NULL OR p.galleryType = :galleryType)
            """)
    Page<GalleryPost> findByTagName(
            @Param("tagName") String tagName,
            @Param("galleryType") GalleryType galleryType,
            Pageable pageable);

    // 제목/설명 키워드 검색
    @Query("""
            SELECT p FROM GalleryPost p
            WHERE p.visibility = 'PUBLIC'
            AND (p.title LIKE %:keyword% OR p.description LIKE %:keyword%)
            """)
    Page<GalleryPost> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 리믹스 원본 참조 게시물 수
    long countByOriginPost_PostId(Long originPostId);

    // 유저가 좋아요한 공개 게시물
    @Query("""
            SELECT p FROM GalleryPost p
            WHERE p.postId IN (
                SELECT l.targetId FROM Like l
                WHERE l.user.userId = :userId AND l.targetType = 'GALLERY_POST'
            )
            AND p.visibility = :visibility
            """)
    Page<GalleryPost> findLikedByUser(
            @Param("userId") Long userId,
            @Param("visibility") Visibility visibility,
            Pageable pageable);
}
