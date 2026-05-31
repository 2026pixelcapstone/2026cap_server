package com.expansion.server.domain.commission.repository;

import com.expansion.server.domain.commission.entity.RequestPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestPostRepository extends JpaRepository<RequestPost, Long> {

    Page<RequestPost> findByStatus(String status, Pageable pageable);

    Page<RequestPost> findByClient_UserId(Long clientId, Pageable pageable);

    /**
     * 공개 목록 검색 — status 고정, keyword는 선택(null이면 무시).
     * keyword는 제목 또는 설명에 대소문자 무시 포함 검색.
     */
    @Query("""
            SELECT p FROM RequestPost p
            WHERE p.status = :status
              AND (:keyword IS NULL
                   OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<RequestPost> search(@Param("status") String status,
                             @Param("keyword") String keyword,
                             Pageable pageable);
}
