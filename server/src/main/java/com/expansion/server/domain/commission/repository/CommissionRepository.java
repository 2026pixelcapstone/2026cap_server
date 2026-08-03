package com.expansion.server.domain.commission.repository;

import com.expansion.server.domain.commission.entity.Commission;
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

public interface CommissionRepository extends JpaRepository<Commission, Long> {

    Page<Commission> findByClient_UserId(Long clientId, Pageable pageable);

    Page<Commission> findByArtist_UserId(Long artistId, Pageable pageable);

    Page<Commission> findByStatus(String status, Pageable pageable);

    /** 결제 승인 시 payment_id로 대상 커미션을 역참조(결제 prepare 때 미리 연결해 둠). */
    Optional<Commission> findByPaymentId(Long paymentId);

    /**
     * 결제 준비(prepare) 시 커미션 행을 비관적 락으로 잡아 동시 요청을 직렬화한다.
     * (의뢰자 "결제하기" 연타로 PENDING 결제가 이중 생성돼 고아 행이 남는 것 방지.)
     * prepare는 외부 API 호출이 없는 순수 DB 작업이라 락을 쥐어도 I/O 대기 문제가 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Commission c WHERE c.commissionId = :id")
    Optional<Commission> findByIdForUpdate(@Param("id") Long id);

    // 진행 중(IN_PROGRESS/REVIEW 등) 거래를 양쪽 역할(의뢰자/작가) 합쳐 상태로 서버 필터.
    // "거래룸 상시 진입점"(네비 배지/드롭다운·커미션 배너·메인 카드)에서 사용 — 페이지 없이 전체 반환.
    @Query("""
            SELECT c FROM Commission c
            WHERE (c.client.userId = :userId OR c.artist.userId = :userId)
            AND c.status IN :statuses
            ORDER BY c.createdAt DESC
            """)
    List<Commission> findActiveByUser(@Param("userId") Long userId, @Param("statuses") List<String> statuses);

    // 지원(application) → 생성된 커미션 매핑 (지원자 목록에 거래룸/취소 상태 노출용)
    List<Commission> findByApplicationIdIn(List<Long> applicationIds);

    // 해당 커미션의 당사자(의뢰자/작가) 여부 — 엔티티 지연로딩 없이 검증 (WS 구독 권한 등)
    @Query("""
            SELECT COUNT(c) > 0 FROM Commission c
            WHERE c.commissionId = :commissionId
            AND (c.client.userId = :userId OR c.artist.userId = :userId)
            """)
    boolean isParty(@Param("commissionId") Long commissionId, @Param("userId") Long userId);

    // 의뢰글 삭제 시 — 성사된 계약은 거래 기록이므로 보존하고 의뢰글/지원 참조만 끊음(FK null).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Commission c SET c.requestPostId = null, c.applicationId = null WHERE c.requestPostId = :requestPostId")
    void detachFromRequestPost(@Param("requestPostId") Long requestPostId);

    // 작가별 완료 거래 건수 배치 (신뢰 신호 — 카드 N+1 방지)
    @Query("""
            SELECT c.artist.userId AS artistId, COUNT(c) AS count
            FROM Commission c
            WHERE c.artist.userId IN :artistIds AND c.status = 'COMPLETED'
            GROUP BY c.artist.userId
            """)
    List<ArtistCompletedRow> findCompletedCountByArtistIds(@Param("artistIds") List<Long> artistIds);

    interface ArtistCompletedRow {
        Long getArtistId();
        Long getCount();
    }
}
