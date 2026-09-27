package ai.mintpop.lane.controller;

import ai.mintpop.lane.client.StripeGateway;
import ai.mintpop.lane.client.StripeWebhookEvent;
import ai.mintpop.lane.config.PaymentProperties;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import ai.mintpop.lane.enumeration.OrderStatus;
import ai.mintpop.lane.repository.PlanOrderRepository;
import ai.mintpop.lane.repository.PlanRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.OrderSettledListener;
import ai.mintpop.lane.service.PaymentService;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class PaymentControllerTest extends MysqlTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private PlanOrderRepository orderRepository;
    @Autowired private SessionTokenService sessionTokenService;
    @Autowired private PaymentProperties paymentProperties;
    @Autowired private PaymentService paymentService;

    /** 不打网络：Stripe 一律替身 */
    @MockitoBean private StripeGateway stripeGateway;

    /** 替身入账通知回调，验证入账通知契约 */
    @MockitoBean private OrderSettledListener orderSettledListener;

    private Long buyerId;
    private Long otherId;
    private Long planId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String createOrder(Long userId) throws Exception {
        String body = mockMvc.perform(post("/api/orders").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planId\":" + planId + "}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("orderNo").asText();
    }

    private static PaymentIntent intent(String id, String status, String clientSecret, Long amount, String currency) {
        PaymentIntent pi = new PaymentIntent();
        pi.setId(id);
        pi.setStatus(status);
        pi.setClientSecret(clientSecret);
        pi.setAmount(amount);
        pi.setCurrency(currency);
        return pi;
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        buyerId = fixtures.createUser("logto-buyer", null, null);
        otherId = fixtures.createUser("logto-other", null, null);
        planId = DatabaseFixtures.createPlan(planRepository, "Claude 月付", AgentType.CLAUDE, 30, "99.99", true);
        // 测试配置里没有密钥；这里临时填上，让「已配置」分支可测。@AfterEach 复原
        paymentProperties.setSecretKey("sk_unit_test_placeholder");
        paymentProperties.setPublishableKey("pk_unit_test_placeholder");
    }

    @AfterEach
    void tearDown() {
        paymentProperties.setSecretKey(null);
        paymentProperties.setPublishableKey(null);
    }

    @Test
    @DisplayName("收银台信息：已配置返回 stripe 与 publishable key；未配置方法列表为空、key 为 null")
    void checkoutInfo() throws Exception {
        mockMvc.perform(get("/api/payment/checkout-info").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.data.methods[0]").value("stripe"))
                .andExpect(jsonPath("$.data.stripePublishableKey").value("pk_unit_test_placeholder"));
        paymentProperties.setSecretKey(null);
        mockMvc.perform(get("/api/payment/checkout-info").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.data.methods").isEmpty())
                .andExpect(jsonPath("$.data.stripePublishableKey").doesNotExist());
    }

    @Test
    @DisplayName("首次发起支付：按套餐币种与最小单位金额创建 intent，落交易号，返回 client_secret 与剩余秒数")
    void createIntentFirstTime() throws Exception {
        String orderNo = createOrder(buyerId);
        when(stripeGateway.createPaymentIntent(eq(orderNo), eq(9999L), eq(Currency.USD), anyString(), anyList()))
                .thenReturn(intent("pi_1", "requires_payment_method", "pi_1_secret", 9999L, "usd"));

        mockMvc.perform(post("/api/payment/orders/" + orderNo + "/intent").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.clientSecret").value("pi_1_secret"))
                .andExpect(jsonPath("$.data.amountMinor").value(9999))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.productName").value("Claude 月付"))
                .andExpect(jsonPath("$.data.expireRemainingSeconds").isNumber());

        verify(stripeGateway).createPaymentIntent(eq(orderNo), eq(9999L), eq(Currency.USD), eq("Claude 月付"),
                eq(List.of("card", "alipay", "wechat_pay")));
        PlanOrder order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(order.getPaymentTradeNo()).isEqualTo("pi_1");
        assertThat(order.getPaymentProvider()).isEqualTo("stripe");
    }

    @Test
    @DisplayName("并发发起支付：后落号请求自动收敛到先落号的 intent，并撤掉自己创建的孤儿 intent")
    void createIntentRaceConditionConvergesToWinner() throws Exception {
        String orderNo = createOrder(buyerId);
        Long orderId = orderRepository.findByOrderNo(orderNo).orElseThrow().getId();
        // 模拟并发：本请求创建 intent 拿到 pi_loser 之后、自己落号之前，另一个并发请求已经抢先落号成功
        when(stripeGateway.createPaymentIntent(eq(orderNo), eq(9999L), eq(Currency.USD), anyString(), anyList()))
                .thenAnswer(invocation -> {
                    orderRepository.attachPaymentIntent(orderId, "stripe", "pi_winner");
                    return intent("pi_loser", "requires_payment_method", "pi_loser_secret", 9999L, "usd");
                });
        when(stripeGateway.retrievePaymentIntent("pi_winner"))
                .thenReturn(intent("pi_winner", "requires_payment_method", "pi_winner_secret", 9999L, "usd"));

        mockMvc.perform(post("/api/payment/orders/" + orderNo + "/intent").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.clientSecret").value("pi_winner_secret"));

        verify(stripeGateway).cancelPaymentIntent("pi_loser");
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getPaymentTradeNo()).isEqualTo("pi_winner");
    }

    @Test
    @DisplayName("再次进入支付页复用已有 intent；Stripe 侧已 canceled 则报 510003")
    void reuseIntent() throws Exception {
        String orderNo = createOrder(buyerId);
        orderRepository.attachPaymentIntent(orderRepository.findByOrderNo(orderNo).orElseThrow().getId(), "stripe", "pi_old");
        when(stripeGateway.retrievePaymentIntent("pi_old"))
                .thenReturn(intent("pi_old", "requires_payment_method", "pi_old_secret", 9999L, "usd"));

        mockMvc.perform(post("/api/payment/orders/" + orderNo + "/intent").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.data.clientSecret").value("pi_old_secret"));
        verify(stripeGateway, never()).createPaymentIntent(anyString(), anyLong(), any(), anyString(), anyList());

        when(stripeGateway.retrievePaymentIntent("pi_old"))
                .thenReturn(intent("pi_old", "canceled", "pi_old_secret", 9999L, "usd"));
        mockMvc.perform(post("/api/payment/orders/" + orderNo + "/intent").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(510003));
    }

    @Test
    @DisplayName("别人的单发起支付报 510002；已支付 / 已取消的单报 510003；支付未配置报 510005")
    void intentGuards() throws Exception {
        String orderNo = createOrder(buyerId);
        mockMvc.perform(post("/api/payment/orders/" + orderNo + "/intent").header("Authorization", bearer(otherId)))
                .andExpect(jsonPath("$.code").value(510002));

        orderRepository.markCancelled(orderNo);
        mockMvc.perform(post("/api/payment/orders/" + orderNo + "/intent").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(510003));

        String another = createOrder(buyerId);
        paymentProperties.setSecretKey(null);
        mockMvc.perform(post("/api/payment/orders/" + another + "/intent").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(510005));
    }

    @Test
    @DisplayName("超时未付的单发起支付：懒惰过期并报 510003，不打网关")
    void timedOutOrderNotPayable() throws Exception {
        String orderNo = createOrder(buyerId);
        jdbc.update("UPDATE plan_order SET created_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 31 MINUTE) WHERE order_no = ?", orderNo);

        mockMvc.perform(post("/api/payment/orders/" + orderNo + "/intent").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(510003));
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(stripeGateway, never()).createPaymentIntent(anyString(), anyLong(), any(), anyString(), anyList());
    }

    @Test
    @DisplayName("verify：网关侧 succeeded 则入账置 PAID，并同事务建出一条待开通订阅，订单回写 subscriptionId")
    void verifySettlesAndCreatesPendingSubscription() throws Exception {
        String orderNo = createOrder(buyerId);
        Long orderId = orderRepository.findByOrderNo(orderNo).orElseThrow().getId();
        orderRepository.attachPaymentIntent(orderId, "stripe", "pi_1");
        when(stripeGateway.retrievePaymentIntent("pi_1"))
                .thenReturn(intent("pi_1", "succeeded", "s", 9999L, "usd"));

        mockMvc.perform(post("/api/payment/orders/verify").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        PlanOrder paid = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(paid.getSubscriptionId()).isNotNull();

        List<SubscriptionDto> subs = subscriptionRepository.findByUserId(buyerId);
        assertThat(subs).hasSize(1);
        SubscriptionDto s = subs.get(0);
        assertThat(s.getId()).isEqualTo(paid.getSubscriptionId());
        assertThat(s.getPlanId()).isEqualTo(planId);
        assertThat(s.getName()).isEqualTo("Claude 月付");
        assertThat(s.getAgentType()).isEqualTo(AgentType.CLAUDE);
        assertThat(s.getPlanDurationDays()).isEqualTo(30);
        assertThat(s.getPlanPrice()).isEqualByComparingTo("99.99");
        assertThat(s.getPlanCurrency()).isEqualTo(Currency.USD);
        assertThat(s.getStartsAt()).isNull();
        assertThat(s.getEndsAt()).isNull();
        assertThat(s.getAccountEmail()).isNull();
        assertThat(s.getEnterpriseId()).isNull();
        assertThat(s.getCredential()).isNull();
        assertThat(s.getRemark()).isEqualTo("自助购买，订单 " + orderNo);
    }

    @Test
    @DisplayName("verify：订单本地已超时未标记但 Stripe 侧已 succeeded，入账优先，结果是 PAID 而非 EXPIRED")
    void verifySettlesEvenAfterLocalTimeout() throws Exception {
        String orderNo = createOrder(buyerId);
        Long orderId = orderRepository.findByOrderNo(orderNo).orElseThrow().getId();
        orderRepository.attachPaymentIntent(orderId, "stripe", "pi_1");
        jdbc.update("UPDATE plan_order SET created_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 31 MINUTE) WHERE order_no = ?", orderNo);
        when(stripeGateway.retrievePaymentIntent("pi_1"))
                .thenReturn(intent("pi_1", "succeeded", "s", 9999L, "usd"));

        mockMvc.perform(post("/api/payment/orders/verify").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(subscriptionRepository.findByUserId(buyerId)).hasSize(1);
    }

    @Test
    @DisplayName("入账重放不重复建订阅；金额或币种不符拒绝入账")
    void settleIsIdempotentAndChecksAmount() throws Exception {
        String orderNo = createOrder(buyerId);
        paymentService.settlePaid(orderNo, "pi_1", 9999L, "usd");
        paymentService.settlePaid(orderNo, "pi_1", 9999L, "USD");
        assertThat(subscriptionRepository.findByUserId(buyerId)).hasSize(1);

        String second = createOrder(buyerId);
        paymentService.settlePaid(second, "pi_2", 9998L, "usd");
        paymentService.settlePaid(second, "pi_2", 9999L, "cny");
        assertThat(orderRepository.findByOrderNo(second).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(subscriptionRepository.findByUserId(buyerId)).hasSize(1);
    }

    @Test
    @DisplayName("入账交易号与订单已挂的 intent 不一致时拒绝：状态不变、不建订阅")
    void settleRejectsMismatchedIntentId() throws Exception {
        String orderNo = createOrder(buyerId);
        Long orderId = orderRepository.findByOrderNo(orderNo).orElseThrow().getId();
        orderRepository.attachPaymentIntent(orderId, "stripe", "pi_a");

        paymentService.settlePaid(orderNo, "pi_b", 9999L, "usd");

        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(subscriptionRepository.findByUserId(buyerId)).isEmpty();
    }

    @Test
    @DisplayName("取消或过期后钱到账仍入账建订阅（钱已收必须履约）")
    void settleAfterCancelStillFulfils() throws Exception {
        String orderNo = createOrder(buyerId);
        orderRepository.markCancelled(orderNo);
        paymentService.settlePaid(orderNo, "pi_1", 9999L, "usd");
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(subscriptionRepository.findByUserId(buyerId)).hasSize(1);
    }

    @Test
    @DisplayName("webhook 事件：成功入账、失败置 FAILED、别的业务线跳过、无关类型忽略")
    void handleWebhookRouting() throws Exception {
        String orderNo = createOrder(buyerId);
        paymentService.handleWebhook(new StripeWebhookEvent("payment_intent.payment_failed", "pi_1", orderNo, "lane", 9999L, "usd"));
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);

        paymentService.handleWebhook(new StripeWebhookEvent("payment_intent.succeeded", "pi_1", orderNo, "shop", 9999L, "usd"));
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);

        paymentService.handleWebhook(new StripeWebhookEvent("charge.refunded", "pi_1", orderNo, "lane", 9999L, "usd"));
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.FAILED);

        paymentService.handleWebhook(new StripeWebhookEvent("payment_intent.succeeded", "pi_1", orderNo, "lane", 9999L, "usd"));
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        // 查无此单与无 product 标记的旧事件都不抛
        paymentService.handleWebhook(new StripeWebhookEvent("payment_intent.succeeded", "pi_x", "LN0", null, 1L, "usd"));
    }

    @Test
    @DisplayName("取消订单联动撤销已挂的 Stripe intent；未发起过支付的订单取消不打网关")
    void cancelCancelsAttachedStripeIntent() throws Exception {
        // 先验证未发起过支付（无交易号）的订单取消不打网关
        String freshOrderNo = createOrder(buyerId);
        mockMvc.perform(post("/api/orders/" + freshOrderNo + "/cancel").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(0));
        verify(stripeGateway, never()).cancelPaymentIntent(anyString());

        String orderNo = createOrder(buyerId);
        Long orderId = orderRepository.findByOrderNo(orderNo).orElseThrow().getId();
        orderRepository.attachPaymentIntent(orderId, "stripe", "pi_1");

        mockMvc.perform(post("/api/orders/" + orderNo + "/cancel").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(0));
        verify(stripeGateway).cancelPaymentIntent("pi_1");
    }

    @Test
    @DisplayName("懒惰过期（订单列表入口）联动撤销已挂的 Stripe intent")
    void lazyExpiryOnListCancelsAttachedStripeIntent() throws Exception {
        String orderNo = createOrder(buyerId);
        Long orderId = orderRepository.findByOrderNo(orderNo).orElseThrow().getId();
        orderRepository.attachPaymentIntent(orderId, "stripe", "pi_2");
        jdbc.update("UPDATE plan_order SET created_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 31 MINUTE) WHERE order_no = ?", orderNo);

        mockMvc.perform(get("/api/orders").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(stripeGateway).cancelPaymentIntent("pi_2");
    }

    @Test
    @DisplayName("verify：未发起过支付直接回当前状态；已超时则过期；别人的单报 510002")
    void verifyWithoutIntentAndExpiry() throws Exception {
        String orderNo = createOrder(buyerId);
        mockMvc.perform(post("/api/payment/orders/verify").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        verify(stripeGateway, never()).retrievePaymentIntent(anyString());

        jdbc.update("UPDATE plan_order SET created_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 31 MINUTE) WHERE order_no = ?", orderNo);
        mockMvc.perform(post("/api/payment/orders/verify").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));

        mockMvc.perform(post("/api/payment/orders/verify").header("Authorization", bearer(otherId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value(510002));
    }

    @Test
    @DisplayName("入账通知契约：首次入账恰好通知一次订单号；重放不再通知；通知抛异常不影响入账与建订阅")
    void orderSettledListenerContract() throws Exception {
        String orderNo = createOrder(buyerId);
        paymentService.settlePaid(orderNo, "pi_1", 9999L, "usd");
        paymentService.settlePaid(orderNo, "pi_1", 9999L, "usd");
        verify(orderSettledListener, times(1)).onSettled(orderNo);

        String second = createOrder(buyerId);
        doThrow(new RuntimeException("飞书不可达（模拟）")).when(orderSettledListener).onSettled(second);
        paymentService.settlePaid(second, "pi_2", 9999L, "usd");

        assertThat(orderRepository.findByOrderNo(second).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(subscriptionRepository.findByUserId(buyerId)).hasSize(2);
    }
}
