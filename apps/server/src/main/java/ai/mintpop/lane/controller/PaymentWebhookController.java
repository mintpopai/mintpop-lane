package ai.mintpop.lane.controller;

import ai.mintpop.lane.client.StripeGateway;
import ai.mintpop.lane.client.StripeWebhookEvent;
import ai.mintpop.lane.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Stripe webhook 回调。机器对机器接口，不走 ApiResponse：验签失败 / 缺签名 / 超长回 400，
 * 处理异常回 500（让 Stripe 重试），其余一律 200 空响应。
 * 异常必须在本控制器内消化——漏给全局异常处理器会被包装成 HTTP 200，Stripe 将视为已送达。
 */
@Slf4j
@RestController
public class PaymentWebhookController {

    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private final StripeGateway stripeGateway;
    private final PaymentService paymentService;

    public PaymentWebhookController(StripeGateway stripeGateway, PaymentService paymentService) {
        this.stripeGateway = stripeGateway;
        this.paymentService = paymentService;
    }

    @PostMapping("/api/v1/payment/webhook/stripe")
    public ResponseEntity<Void> handle(@RequestBody(required = false) byte[] payload,
                                       @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES || signature == null) {
            return ResponseEntity.badRequest().build();
        }
        StripeWebhookEvent event;
        try {
            event = stripeGateway.parseWebhookEvent(new String(payload, StandardCharsets.UTF_8), signature);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook 验签失败", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Stripe webhook 解析失败", e);
            return ResponseEntity.internalServerError().build();
        }
        try {
            paymentService.handleWebhook(event);
        } catch (Exception e) {
            log.error("Stripe webhook 处理失败 type={}", event.type(), e);
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }
}
