package com.expansion.server.domain.commission.repository;

import com.expansion.server.domain.commission.entity.ArtistService;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArtistServiceRepository extends JpaRepository<ArtistService, Long> {

    Page<ArtistService> findByStatus(String status, Pageable pageable);

    Page<ArtistService> findByArtist_UserId(Long artistId, Pageable pageable);

    /**
     * 서비스 의뢰 시 서비스 행을 비관적 락으로 잡아 같은 서비스에 대한 동시 의뢰를 직렬화한다.
     * (의뢰하기 연타로 '진행 중 거래 있음' 검사를 둘 다 통과해 중복 계약이 생기는 것 방지)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ArtistService s WHERE s.serviceId = :id")
    Optional<ArtistService> findByIdForUpdate(@Param("id") Long id);

    /**
     * 공개 목록 검색 — status 고정, category/keyword는 선택(null이면 무시).
     * keyword는 제목 또는 설명에 대소문자 무시 포함 검색.
     */
    @Query("""
            SELECT s FROM ArtistService s
            WHERE s.status = :status
              AND (:category IS NULL OR s.category = :category)
              AND (:keyword IS NULL
                   OR LOWER(s.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                   OR LOWER(s.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
            """)
    Page<ArtistService> search(@Param("status") String status,
                               @Param("category") String category,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}
