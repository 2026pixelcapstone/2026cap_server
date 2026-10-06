package com.expansion.server.domain.palette.repository;

import com.expansion.server.domain.palette.entity.Palette;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaletteRepository extends JpaRepository<Palette, Long> {

    /**
     * 공개 목록 — 모든 조건은 선택(null이면 무시). 공식 팔레트(user NULL)도 나와야 해서 LEFT JOIN.
     * keywordPattern은 서비스가 소문자 '%키워드%'로 만들어 넘김.
     */
    @Query(value = """
            SELECT p FROM Palette p LEFT JOIN FETCH p.user u
            WHERE p.status = 'ACTIVE'
              AND (:authorId IS NULL OR u.userId = :authorId)
              AND (:minColors IS NULL OR p.colorCount >= :minColors)
              AND (:maxColors IS NULL OR p.colorCount <= :maxColors)
              AND (:keywordPattern IS NULL OR LOWER(p.name) LIKE :keywordPattern)
            """,
            countQuery = """
            SELECT COUNT(p) FROM Palette p LEFT JOIN p.user u
            WHERE p.status = 'ACTIVE'
              AND (:authorId IS NULL OR u.userId = :authorId)
              AND (:minColors IS NULL OR p.colorCount >= :minColors)
              AND (:maxColors IS NULL OR p.colorCount <= :maxColors)
              AND (:keywordPattern IS NULL OR LOWER(p.name) LIKE :keywordPattern)
            """)
    Page<Palette> search(@Param("authorId") Long authorId,
                         @Param("minColors") Integer minColors,
                         @Param("maxColors") Integer maxColors,
                         @Param("keywordPattern") String keywordPattern,
                         Pageable pageable);

    /**
     * 좋아요 수 원자적 증감(동시 토글 시 lost update 방지), 0 미만 방지.
     * flushAutomatically 필수 — 없으면 아직 반영 안 된 좋아요 삭제(likes)가 clear()로 버려져
     * '취소했는데 행이 남는' 버그가 난다(일괄 UPDATE는 다른 테이블의 대기 변경을 자동 flush하지 않음).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Palette p SET p.likeCount = p.likeCount + :delta WHERE p.paletteId = :id AND p.likeCount + :delta >= 0")
    int addLikeCount(@Param("id") Long paletteId, @Param("delta") int delta);
}
