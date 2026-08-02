package com.expansion.server.domain.payment.service;

import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.domain.payment.client.TossPaymentClient;
import com.expansion.server.domain.payment.dto.PaymentConfirmRequest;
import com.expansion.server.domain.payment.dto.PaymentConfirmResult;
import com.expansion.server.domain.payment.dto.PaymentPrepareResponse;
import com.expansion.server.domain.payment.entity.Payment;
import com.expansion.server.domain.payment.repository.PaymentRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    /** 커미션 시작(결제 대기) 상태 — 이 상태에서만 결제 가능. CommissionService와 문자열 공유. */
    public static final String COMMISSION_PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String COMMISSION_IN_PROGRESS = "IN_PROGRESS";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository paymentRepository;
    private final CommissionRepository commissionRepository;
    private final TossPaymentClient tossClient;

    @Value("${toss.client-key:}")
    private String clientKey;

    // ── 1. 결제 준비: orderId 사전 발급 + PENDING 결제 저장 + 커미션에 연결 ──────────
    @Transactional
    public PaymentPrepareResponse prepareCommissionPayment(Long userId, Long commissionId) {
        Commission commission = commissionRepository.findById(commissionId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        // 결제는 의뢰자만, 결제 대기 상태에서만
        if (!commission.getClient().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (!COMMISSION_PENDING_PAYMENT.equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_REQUIRED);
        }
        BigDecimal amount = commission.getAgreedPrice();
        if (amount == null || amount.signum() <= 0) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_REQUIRED);
        }

        // 이미 연결된 PENDING 결제가 있으면 재사용(재시도), 아니면 새로 발급
        Payment payment = null;
        if (commission.getPaymentId() != null) {
            payment = paymentRepository.findById(commission.getPaymentId()).orElse(null);
            if (payment != null && !"PENDING".equals(payment.getStatus())) {
                // 이미 결제 완료(HELD 등)인데 상태가 안 맞으면 중복 결제 방지
                throw new CustomException(ErrorCode.PAYMENT_ALREADY_DONE);
            }
        }
        if (payment == null) {
            String orderId = "commission_" + commissionId + "_" + randomToken();
            payment = paymentRepository.save(Payment.builder()
                    .userId(userId)
                    .orderId(orderId)
                    .amount(amount)
                    .totalCommission(BigDecimal.ZERO)   // MVP: 수수료 0%
                    .method("READY")
                    .status("PENDING")
                    .build());
            commission.setPaymentId(payment.getPaymentId());
        }

        String orderName = "커미션 결제 - " + safeTitle(commission.getTitle());
        return new PaymentPrepareResponse(payment.getOrderId(), payment.getAmount(), orderName, clientKey);
    }

    // ── 2. 결제 승인: 금액 위변조 검증 → 토스 confirm → HELD + 커미션 IN_PROGRESS ──
    @Transactional
    public PaymentConfirmResult confirmCommissionPayment(Long userId, PaymentConfirmRequest req) {
        Payment payment = paymentRepository.findByOrderId(req.orderId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (!"PENDING".equals(payment.getStatus())) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_DONE);
        }
        // 🔴 금액 위변조 검증: 리다이렉트로 돌아온 amount가 서버 사전 저장값과 정확히 일치해야 함
        if (payment.getAmount().compareTo(req.amount()) != 0) {
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        Commission commission = commissionRepository.findByPaymentId(payment.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));
        if (!COMMISSION_PENDING_PAYMENT.equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        // 실제 카드 청구(프로덕션) 또는 목킹(로컬)
        TossPaymentClient.TossConfirmResult result =
                tossClient.confirm(req.paymentKey(), req.orderId(), payment.getAmount());

        payment.markHeld(result.paymentKey(), result.method());   // 플랫폼 보관(에스크로)
        commission.updateStatus(COMMISSION_IN_PROGRESS);          // 작가 작업 시작 가능

        return new PaymentConfirmResult(commission.getCommissionId(), commission.getStatus(), payment.getPaymentId());
    }

    // ── 3. 커미션 완료 시 지급(RELEASED) — CommissionService에서 호출 ──────────────
    /** 완료 확정 시 보관금을 작가 지급 예정으로 전환. 결제 없던 무료 커미션(paymentId null)은 무시. */
    @Transactional
    public void releaseForCommission(Long paymentId) {
        if (paymentId == null) return;
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment != null && "HELD".equals(payment.getStatus())) {
            payment.markReleased();
            // TODO(Phase 3): settlements에 작가 지급 예정 기록 생성
        }
    }

    // ── 4. 커미션 취소 시 환불(REFUNDED) — 배관만. 정책/부분취소는 후속 ─────────────
    /** 취소 시 보관금 환불. HELD 상태만 토스 취소 호출. (RELEASED 이후 환불은 별도 정책) */
    @Transactional
    public void refundForCommission(Long paymentId, String reason) {
        if (paymentId == null) return;
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment != null && "HELD".equals(payment.getStatus())) {
            tossClient.cancel(payment.getPaymentKey(), reason);
            payment.markRefunded();
        }
    }

    // ── helpers ──────────────────────────────────────────────
    private String randomToken() {
        // 8자리 영숫자(orderId 유니크 보강). SecureRandom.
        final String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    private String safeTitle(String title) {
        if (title == null || title.isBlank()) return "커미션";
        return title.length() > 40 ? title.substring(0, 40) : title;
    }
}
