package com.expansion.server.domain.payment.repository;

import com.expansion.server.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** 승인(confirm) 시 orderId로 사전 저장한 결제 건을 찾아 금액 대조. */
    Optional<Payment> findByOrderId(String orderId);
}
