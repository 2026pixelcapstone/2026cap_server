package com.expansion.server.domain.commission.repository;

import com.expansion.server.domain.commission.entity.CommissionApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommissionApplicationRepository extends JpaRepository<CommissionApplication, Long> {

    // 특정 의뢰의 지원 목록
    Page<CommissionApplication> findByRequestPost_RequestPostId(Long requestPostId, Pageable pageable);

    // 의뢰글 삭제 시 지원 레코드 일괄 정리 (계약은 미리 detach되어 FK 영향 없음)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CommissionApplication a WHERE a.requestPost.requestPostId = :requestPostId")
    void deleteByRequestPost_RequestPostId(@Param("requestPostId") Long requestPostId);

    // 작가의 내 지원 목록
    Page<CommissionApplication> findByArtist_UserId(Long artistId, Pageable pageable);

    // 중복 지원 체크
    boolean existsByRequestPost_RequestPostIdAndArtist_UserId(Long requestPostId, Long artistId);

    // 특정 지원 조회 (requestPostId + artistId)
    Optional<CommissionApplication> findByRequestPost_RequestPostIdAndArtist_UserId(Long requestPostId, Long artistId);

    // 수락 시 나머지 지원 REJECTED 처리용
    List<CommissionApplication> findByRequestPost_RequestPostIdAndStatusAndApplicationIdNot(
            Long requestPostId, String status, Long acceptedApplicationId);

    // 의뢰 마감 시 남은 지원 일괄 거절용 (상태별 조회)
    List<CommissionApplication> findByRequestPost_RequestPostIdAndStatus(Long requestPostId, String status);

    // 비관적 락 조회 (동시 수락 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CommissionApplication a WHERE a.applicationId = :id")
    Optional<CommissionApplication> findByIdWithLock(@Param("id") Long id);
}
