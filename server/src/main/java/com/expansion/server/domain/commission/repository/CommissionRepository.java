package com.expansion.server.domain.commission.repository;

import com.expansion.server.domain.commission.entity.Commission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommissionRepository extends JpaRepository<Commission, Long> {

    Page<Commission> findByClient_UserId(Long clientId, Pageable pageable);

    Page<Commission> findByArtist_UserId(Long artistId, Pageable pageable);

    Page<Commission> findByStatus(String status, Pageable pageable);

    // 지원(application) → 생성된 커미션 매핑 (지원자 목록에 거래룸/취소 상태 노출용)
    List<Commission> findByApplicationIdIn(List<Long> applicationIds);
}
