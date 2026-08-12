package com.expansion.server.domain.payment.service;

import com.expansion.server.domain.asset.entity.Asset;
import com.expansion.server.domain.asset.entity.AssetPurchase;
import com.expansion.server.domain.asset.repository.AssetPurchaseRepository;
import com.expansion.server.domain.asset.repository.AssetRepository;
import com.expansion.server.domain.commission.entity.Commission;
import com.expansion.server.domain.commission.repository.CommissionRepository;
import com.expansion.server.domain.payment.client.TossPaymentClient;
import com.expansion.server.domain.payment.dto.PaymentConfirmRequest;
import com.expansion.server.domain.payment.dto.PaymentConfirmResult;
import com.expansion.server.domain.payment.dto.PaymentPrepareResponse;
import com.expansion.server.domain.payment.entity.Payment;
import com.expansion.server.domain.payment.entity.PaymentStatus;
import com.expansion.server.domain.payment.repository.PaymentRepository;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.UserRepository;
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
    private final AssetRepository assetRepository;
    private final AssetPurchaseRepository assetPurchaseRepository;
    private final UserRepository userRepository;
    private final TossPaymentClient tossClient;

    @Value("${toss.client-key:}")
    private String clientKey;

    // ── 1. 결제 준비: orderId 사전 발급 + PENDING 결제 저장 + 커미션에 연결 ──────────
    @Transactional
    public PaymentPrepareResponse prepareCommissionPayment(Long userId, Long commissionId) {
        // 비관적 락으로 동시 prepare(결제하기 연타)를 직렬화 → PENDING 결제 이중 생성(고아 행) 방지.
        Commission commission = commissionRepository.findByIdForUpdate(commissionId)
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
            // 커미션 락 이후 결제도 락으로 조회(Commission → Payment 순서 통일)
            payment = paymentRepository.findByIdForUpdate(commission.getPaymentId()).orElse(null);
            if (payment != null && payment.getStatus() != PaymentStatus.PENDING) {
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
                    .status(PaymentStatus.PENDING)
                    .build());
            commission.setPaymentId(payment.getPaymentId());
        } else {
            // 재시도: 그사이 합의금액이 바뀌었을 수 있으니 최신 금액으로 동기화(승인 시 대조 기준 일치)
            payment.updateAmount(amount);
        }

        String orderName = "커미션 결제 - " + safeTitle(commission.getTitle());
        return new PaymentPrepareResponse(payment.getOrderId(), payment.getAmount(), orderName, clientKey);
    }

    // ── 2. 결제 승인 — orderId 프리픽스로 커미션(에스크로)/에셋(즉시판매) 분기 ──
    @Transactional
    public PaymentConfirmResult confirm(Long userId, PaymentConfirmRequest req) {
        if (req.orderId().startsWith("asset_")) return confirmAsset(userId, req);
        return confirmCommission(userId, req);
    }

    // 커미션 결제 승인: 금액 위변조 검증 → 토스 confirm → HELD + 커미션 IN_PROGRESS
    @Transactional
    public PaymentConfirmResult confirmCommission(Long userId, PaymentConfirmRequest req) {
        // 락 순서 Commission → Payment 통일: 먼저 orderId로 paymentId만 얻고(엔티티 미적재),
        // 커미션을 락한 뒤 결제를 락으로 재조회해 최신 금액을 쓴다(동시 prepare의 금액 변경 반영).
        Long paymentId = paymentRepository.findIdByOrderId(req.orderId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        Commission commission = commissionRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMISSION_NOT_FOUND));

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_DONE);
        }
        // 🔴 금액 위변조 검증: 리다이렉트로 돌아온 amount가 서버 사전 저장값과 정확히 일치해야 함
        if (payment.getAmount().compareTo(req.amount()) != 0) {
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (!COMMISSION_PENDING_PAYMENT.equals(commission.getStatus())) {
            throw new CustomException(ErrorCode.INVALID_COMMISSION_STATUS);
        }

        // 실제 카드 청구(프로덕션) 또는 목킹(로컬)
        TossPaymentClient.TossConfirmResult result =
                tossClient.confirm(req.paymentKey(), req.orderId(), payment.getAmount());

        payment.markHeld(result.paymentKey(), result.method());   // 플랫폼 보관(에스크로)
        commission.updateStatus(COMMISSION_IN_PROGRESS);          // 작가 작업 시작 가능

        return new PaymentConfirmResult("COMMISSION", commission.getCommissionId(), null, payment.getPaymentId());
    }

    // ── 에셋 즉시판매: prepare(유료·미구매 검증) → 결제창 → confirm(SUCCESS + 구매 생성) ──
    // 커미션과 달리 에스크로 없음: 결제 성공 즉시 구매 확정 → 다운로드 권한.
    @Transactional
    public PaymentPrepareResponse prepareAssetPayment(Long userId, Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));
        if (asset.isFree() || asset.getPrice() == null || asset.getPrice().signum() <= 0) {
            throw new CustomException(ErrorCode.CANNOT_PURCHASE_FREE_ASSET);
        }
        if (assetPurchaseRepository.existsByUser_UserIdAndAsset_AssetId(userId, assetId)) {
            throw new CustomException(ErrorCode.ALREADY_PURCHASED);
        }
        BigDecimal amount = asset.getPrice();
        if (amount.stripTrailingZeros().scale() > 0) {   // KRW 정수만
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 연타 재시도: 같은 사용자+에셋의 PENDING 결제가 있으면 재사용(orderId 하나로 유지) →
        // orderId가 여러 개 생겨 이중 청구되는 것 방지. 없으면 새로 발급.
        String prefix = "asset_" + assetId + "_";
        Payment payment = paymentRepository
                .findFirstByUserIdAndStatusAndOrderIdStartingWith(userId, PaymentStatus.PENDING, prefix)
                .orElse(null);
        if (payment != null) {
            payment.updateAmount(amount);   // 그사이 가격이 바뀌었을 수 있으니 동기화
        } else {
            payment = paymentRepository.save(Payment.builder()
                    .userId(userId)
                    .orderId(prefix + randomToken())
                    .amount(amount)
                    .totalCommission(BigDecimal.ZERO)
                    .method("READY")
                    .status(PaymentStatus.PENDING)
                    .build());
        }

        String orderName = "에셋 구매 - " + safeTitle(asset.getTitle());
        return new PaymentPrepareResponse(payment.getOrderId(), payment.getAmount(), orderName, clientKey);
    }

    @Transactional
    public PaymentConfirmResult confirmAsset(Long userId, PaymentConfirmRequest req) {
        Payment payment = paymentRepository.findByIdForUpdate(
                        paymentRepository.findIdByOrderId(req.orderId())
                                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND)))
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_DONE);
        }
        if (payment.getAmount().compareTo(req.amount()) != 0) {   // 금액 위변조 검증
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // orderId에서 assetId 추출(서버가 발급·저장한 값이라 신뢰 가능): asset_{id}_{rand}
        Long assetId = Long.valueOf(req.orderId().split("_")[1]);
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.ASSET_NOT_FOUND));
        if (assetPurchaseRepository.existsByUser_UserIdAndAsset_AssetId(userId, assetId)) {
            throw new CustomException(ErrorCode.ALREADY_PURCHASED);   // 동시/중복 승인 방지
        }

        TossPaymentClient.TossConfirmResult result =
                tossClient.confirm(req.paymentKey(), req.orderId(), payment.getAmount());

        payment.markSuccess(result.paymentKey(), result.method());   // 즉시 판매 확정(에스크로 없음)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        assetPurchaseRepository.save(AssetPurchase.builder()
                .user(user).asset(asset)
                .paymentId(payment.getPaymentId())
                .pricePaid(payment.getAmount())
                .build());

        return new PaymentConfirmResult("ASSET", null, assetId, payment.getPaymentId());
    }

    // ── 3. 커미션 완료 시 지급(RELEASED) — CommissionService에서 호출 ──────────────
    /** 완료 확정 시 보관금을 작가 지급 예정으로 전환. 결제 없던 무료 커미션(paymentId null)은 무시. */
    @Transactional
    public void releaseForCommission(Long paymentId) {
        if (paymentId == null) return;
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.HELD) {
            payment.markReleased();
            // TODO(Phase 3): settlements에 작가 지급 예정 기록 생성
        }
    }

    // ── 4. 커미션 취소 시 환불(REFUNDED) — 배관만. 정책/부분취소는 후속 ─────────────
    /** 취소 시 보관금 환불. HELD 상태만 토스 취소 호출. (RELEASED 이후 환불은 별도 정책) */
    @Transactional
    public void refundForCommission(Long paymentId, String reason) {
        if (paymentId == null) return;
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.HELD) {
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
