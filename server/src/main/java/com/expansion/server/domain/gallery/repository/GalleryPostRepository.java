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

    // 여러 태그명 중 하나라도 일치하는 PUBLIC 게시물 (AI 컨셉 도우미 관련 작품용, 좋아요순)
    // tagNames 는 소문자로 정규화해서 넘긴다(대소문자 무시 매칭).
    @Query("""
            SELECT DISTINCT p FROM GalleryPost p
            JOIN p.postTags pt
            JOIN pt.tag t
            WHERE LOWER(t.tagName) IN :tagNames
            AND p.visibility = 'PUBLIC'
            ORDER BY p.likeCount DESC
            """)
    List<GalleryPost> findByAnyTagNames(@Param("tagNames") List<String> tagNames, Pageable pageable);

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

    // ── 메인페이지(5-A) ─────────────────────────────────────

    /**
     * 최근 좋아요(since 이후) 많이 받은 PUBLIC 작품 id — 자유·전용 합침. 같은 수면 최근 좋아요·id 순으로 고정.
     * GROUP BY는 원시 컬럼(p.post_id)만(TROUBLESHOOTING: 식/CASE 그룹은 Postgres가 거부해 실호출 500).
     */
    @Query(value = """
            SELECT p.post_id
            FROM gallery_posts p
            JOIN likes l ON l.target_id = p.post_id AND l.target_type = 'GALLERY_POST' AND l.created_at >= :since
            WHERE p.visibility = 'PUBLIC'
            GROUP BY p.post_id
            ORDER BY COUNT(*) DESC, MAX(l.created_at) DESC, p.post_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findTrendingPostIds(@Param("since") java.time.LocalDateTime since, @Param("limit") int limit);

    /** 누적 좋아요순 PUBLIC 작품(이번 주 인기가 모자랄 때 채우는 용도) — id로 순서 고정 */
    @Query("""
            SELECT p FROM GalleryPost p
            WHERE p.visibility = 'PUBLIC'
            ORDER BY p.likeCount DESC, p.createdAt DESC, p.postId DESC
            """)
    List<GalleryPost> findTopByLikeCount(Pageable pageable);

    /** 내가 팔로우한 작가들의 최신 PUBLIC 작품 */
    @Query(value = """
            SELECT p FROM GalleryPost p
            WHERE p.visibility = 'PUBLIC'
              AND p.user.userId IN (SELECT f.following.userId FROM Follow f WHERE f.follower.userId = :userId)
            ORDER BY p.createdAt DESC, p.postId DESC
            """,
            countQuery = """
            SELECT COUNT(p) FROM GalleryPost p
            WHERE p.visibility = 'PUBLIC'
              AND p.user.userId IN (SELECT f.following.userId FROM Follow f WHERE f.follower.userId = :userId)
            """)
    Page<GalleryPost> findFollowingFeed(@Param("userId") Long userId, Pageable pageable);

    /**
     * 최근 좋아요(since 이후)를 많이 받은 작가 — [user_id, 받은 좋아요 수]. PUBLIC 작품만 집계,
     * 탈퇴·정지 계정과 비공개 프로필은 제외. 같은 수면 user_id로 순서 고정.
     */
    @Query(value = """
            SELECT p.user_id, COUNT(*) AS cnt
            FROM likes l
            JOIN gallery_posts p ON p.post_id = l.target_id
            JOIN users u ON u.user_id = p.user_id
            JOIN profiles pr ON pr.user_id = p.user_id
            WHERE l.target_type = 'GALLERY_POST' AND l.created_at >= :since
              AND p.visibility = 'PUBLIC' AND u.status = 'ACTIVE' AND pr.is_public = TRUE
            GROUP BY p.user_id
            ORDER BY cnt DESC, p.user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findPopularAuthors(@Param("since") java.time.LocalDateTime since, @Param("limit") int limit);
}
