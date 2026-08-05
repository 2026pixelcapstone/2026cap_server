package com.expansion.server.domain.payment.repository;

import com.expansion.server.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * orderId → paymentId만 조회(엔티티를 영속성 컨텍스트에 올리지 않음).
     * confirm이 커미션 락을 잡기 전에 stale 결제 엔티티를 캐시에 올리는 것을 피하기 위함 —
     * 커미션 락 후 {@link #findByIdForUpdate}로 최신 결제를 다시 읽는다.
     */
    @Query("SELECT p.paymentId FROM Payment p WHERE p.orderId = :orderId")
    Optional<Long> findIdByOrderId(@Param("orderId") String orderId);

    /**
     * 결제 행을 비관적 락으로 조회. 락 순서는 항상 Commission → Payment 로 통일해
     * (prepare·confirm 모두) 데드락과 stale 금액 사용을 방지한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
