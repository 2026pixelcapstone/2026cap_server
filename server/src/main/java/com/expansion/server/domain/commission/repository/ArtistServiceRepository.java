package com.expansion.server.domain.commission.repository;

import com.expansion.server.domain.commission.entity.ArtistService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtistServiceRepository extends JpaRepository<ArtistService, Long> {

    Page<ArtistService> findByStatus(String status, Pageable pageable);

    Page<ArtistService> findByArtist_UserId(Long artistId, Pageable pageable);

    /**
     * 공개 목록 검색 — status 고정, category/keyword는 선택(null이면 무시).
     * keyword는 제목 또는 설명에 대소문자 무시 포함 검색.
     */
    @Query("""
            SELECT s FROM ArtistService s
            WHERE s.status = :status
              AND (:category IS NULL OR s.category = :category)
              AND (:keyword IS NULL
                   OR LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<ArtistService> search(@Param("status") String status,
                               @Param("category") String category,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}
