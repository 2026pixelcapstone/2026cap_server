package com.expansion.server.domain.payment.controller;

import com.expansion.server.domain.payment.dto.PaymentConfirmRequest;
import com.expansion.server.domain.payment.dto.PaymentConfirmResult;
import com.expansion.server.domain.payment.dto.PaymentPrepareResponse;
import com.expansion.server.domain.payment.service.PaymentService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 준비 — 의뢰자가 결제 대기(PENDING_PAYMENT) 커미션에 대해 orderId를 발급받는다.
     * 프론트는 응답값으로 결제위젯 requestPayment 호출.
     */
    @PostMapping("/commission/{commissionId}/prepare")
    public ApiResponse<PaymentPrepareResponse> prepareCommission(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long commissionId) {
        return ApiResponse.ok(paymentService.prepareCommissionPayment(userId, commissionId));
    }

    /**
     * 에셋 결제 준비 — 유료·미구매 에셋에 대해 orderId 발급(에스크로 없이 즉시판매).
     */
    @PostMapping("/asset/{assetId}/prepare")
    public ApiResponse<PaymentPrepareResponse> prepareAsset(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId) {
        return ApiResponse.ok(paymentService.prepareAssetPayment(userId, assetId));
    }

    /**
     * 결제 승인 — 토스 인증 후 successUrl에서 돌아온 값으로 실제 승인.
     * orderId 프리픽스로 커미션(HELD+IN_PROGRESS)/에셋(SUCCESS+구매 생성) 분기.
     */
    @PostMapping("/confirm")
    public ApiResponse<PaymentConfirmResult> confirm(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaymentConfirmRequest request) {
        return ApiResponse.ok(paymentService.confirm(userId, request));
    }
}
