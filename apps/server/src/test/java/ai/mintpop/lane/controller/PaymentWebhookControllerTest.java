package ai.mintpop.lane.controller;

import ai.mintpop.lane.client.StripeGateway;
import ai.mintpop.lane.client.StripeWebhookEvent;
import ai.mintpop.lane.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 故意不挂 GlobalExceptionHandler：webhook 的异常必须在控制器内消化成原生状态码 */
class PaymentWebhookControllerTest {

    private static final String PATH = "/api/v1/payment/webhook/stripe";

    private MockMvc mockMvc;
    private StripeGateway stripeGateway;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        stripeGateway = mock(StripeGateway.class);
        paymentService = mock(PaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentWebhookController(stripeGateway, paymentService)).build();
    }

    @Test
    @DisplayName("验签通过：处理事件并回 200")
    void validEventReturns200() throws Exception {
        StripeWebhookEvent event = new StripeWebhookEvent("payment_intent.succeeded", "pi_1", "LN1", "lane", 9999L, "usd");
        when(stripeGateway.parseWebhookEvent(anyString(), eq("sig"))).thenReturn(event);
        mockMvc.perform(post(PATH).header("Stripe-Signature", "sig").content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isOk());
        verify(paymentService).handleWebhook(event);
    }

    @Test
    @DisplayName("验签失败、缺签名头、超过 1MB 都回 400")
    void badRequests() throws Exception {
        when(stripeGateway.parseWebhookEvent(anyString(), anyString()))
                .thenThrow(new SignatureVerificationException("bad", "sig"));
        mockMvc.perform(post(PATH).header("Stripe-Signature", "bad").content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH).header("Stripe-Signature", "sig").content(new byte[1024 * 1024 + 1]))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("解析阶段非验签异常与业务处理异常都回 500，让 Stripe 重试，绝不被包装成 200")
    void serverErrors() throws Exception {
        when(stripeGateway.parseWebhookEvent(anyString(), anyString())).thenThrow(new RuntimeException("未配置"));
        mockMvc.perform(post(PATH).header("Stripe-Signature", "sig").content("{}"))
                .andExpect(status().isInternalServerError());

        StripeWebhookEvent event = new StripeWebhookEvent("payment_intent.succeeded", "pi_1", "LN1", "lane", 9999L, "usd");
        // 重新打桩用 doReturn().when()：上一步已把该方法桩成抛异常，若仍用 when(mock.call()).thenReturn()，
        // 括号里那次真实调用会先触发旧桩的异常，doReturn().when() 不会重放旧行为
        doReturn(event).when(stripeGateway).parseWebhookEvent(anyString(), anyString());
        doThrow(new RuntimeException("db down")).when(paymentService).handleWebhook(any());
        mockMvc.perform(post(PATH).header("Stripe-Signature", "sig").content("{}"))
                .andExpect(status().isInternalServerError());
    }
}
