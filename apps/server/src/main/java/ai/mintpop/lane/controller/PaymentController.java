package ai.mintpop.lane.controller;

import ai.mintpop.lane.request.VerifyOrderRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.CheckoutInfoResponse;
import ai.mintpop.lane.response.PaymentIntentResponse;
import ai.mintpop.lane.response.VerifyOrderResponse;
import ai.mintpop.lane.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 支付接口（登录即可；订单归属在服务层校验）。子方式只在前端展示层，本控制器不感知 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/checkout-info")
    public ApiResponse<CheckoutInfoResponse> checkoutInfo() {
        return ApiResponse.success(paymentService.checkoutInfo());
    }

    @PostMapping("/orders/{orderNo}/intent")
    public ApiResponse<PaymentIntentResponse> createIntent(@AuthenticationPrincipal Long userId,
                                                           @PathVariable String orderNo) {
        return ApiResponse.success(paymentService.getOrCreateIntent(userId, orderNo));
    }

    @PostMapping("/orders/verify")
    public ApiResponse<VerifyOrderResponse> verify(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody VerifyOrderRequest request) {
        return ApiResponse.success(paymentService.verify(userId, request.getOrderNo()));
    }
}
