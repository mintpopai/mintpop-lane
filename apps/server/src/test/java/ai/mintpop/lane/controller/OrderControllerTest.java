package ai.mintpop.lane.controller;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.OrderStatus;
import ai.mintpop.lane.repository.PlanOrderRepository;
import ai.mintpop.lane.repository.PlanRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static ai.mintpop.lane.enumeration.UserStatus.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class OrderControllerTest extends MysqlTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private PlanOrderRepository orderRepository;
    @Autowired private SessionTokenService sessionTokenService;

    private Long buyerId;
    private Long otherId;
    private Long suspendedId;
    private Long monthlyPlanId;
    private Long disabledPlanId;
    private DatabaseFixtures fixtures;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String createOrder(Long userId, Long planId) throws Exception {
        String body = mockMvc.perform(post("/api/orders").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + planId + "}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("orderNo").asText();
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        buyerId = fixtures.createUser("logto-buyer", null);
        otherId = fixtures.createUser("logto-other", null);
        suspendedId = fixtures.createUser("logto-suspended", MEMBER, SUSPENDED, null);
        monthlyPlanId = DatabaseFixtures.createPlan(planRepository, "Claude 月付", AgentType.CLAUDE, 30, "99.99", true);
        disabledPlanId = DatabaseFixtures.createPlan(planRepository, "已下架", AgentType.CLAUDE, 30, "1.00", false);
    }

    @Test
    @DisplayName("下单：套餐信息落快照，金额换算成最小单位，订单号 LN 开头，状态待支付")
    void createSnapshotsPlan() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + monthlyPlanId + "}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo", matchesPattern("LN[0-9]{20}")))
                .andExpect(jsonPath("$.data.amountMinor").value(9999))
                .andExpect(jsonPath("$.data.currency").value("USD"));

        PlanOrder order = orderRepository.findByUserId(buyerId, 10).get(0);
        assertThat(order.getName()).isEqualTo("Claude 月付");
        assertThat(order.getAgentType()).isEqualTo(AgentType.CLAUDE);
        assertThat(order.getPlanDurationDays()).isEqualTo(30);
        assertThat(order.getPlanPrice()).isEqualByComparingTo("99.99");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentTradeNo()).isNull();
    }

    @Test
    @DisplayName("下架或不存在的套餐报 510001")
    void rejectsUnavailablePlan() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + disabledPlanId + "}"))
                .andExpect(jsonPath("$.code").value(510001));
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":99999}"))
                .andExpect(jsonPath("$.code").value(510001));
    }

    @Test
    @DisplayName("非 ACTIVE 用户不能下单，报 510007")
    void rejectsSuspendedUser() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(suspendedId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + monthlyPlanId + "}"))
                .andExpect(jsonPath("$.code").value(510007));
    }

    @Test
    @DisplayName("不传套餐报 110001")
    void missingPlanIdFailsValidation() throws Exception {
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("列表只见本人订单，按创建倒序；别人的单按不存在处理（510002）")
    void listAndGetAreScopedToOwner() throws Exception {
        String first = createOrder(buyerId, monthlyPlanId);
        String second = createOrder(buyerId, monthlyPlanId);
        createOrder(otherId, monthlyPlanId);

        mockMvc.perform(get("/api/orders").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].orderNo").value(second))
                .andExpect(jsonPath("$.data[1].orderNo").value(first))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].amountMinor").value(9999));

        mockMvc.perform(get("/api/orders/" + first).header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.data.orderNo").value(first));
        mockMvc.perform(get("/api/orders/" + first).header("Authorization", bearer(otherId)))
                .andExpect(jsonPath("$.code").value(510002));
    }

    @Test
    @DisplayName("取消：待支付可取消，取消后再取消或再支付都不行")
    void cancelPendingOrder() throws Exception {
        String orderNo = createOrder(buyerId, monthlyPlanId);
        mockMvc.perform(post("/api/orders/" + orderNo + "/cancel").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        mockMvc.perform(post("/api/orders/" + orderNo + "/cancel").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(510004));
        mockMvc.perform(post("/api/orders/" + orderNo + "/cancel").header("Authorization", bearer(otherId)))
                .andExpect(jsonPath("$.code").value(510002));
    }

    @Test
    @DisplayName("超时未付的订单在列表入口被懒惰置为 EXPIRED")
    void listExpiresTimedOutOrders() throws Exception {
        String orderNo = createOrder(buyerId, monthlyPlanId);
        // 把创建时间拨回 31 分钟前（默认时限 30 分钟）
        jdbc.update("UPDATE plan_order SET created_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 31 MINUTE) WHERE order_no = ?", orderNo);

        mockMvc.perform(get("/api/orders").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.data[0].status").value("EXPIRED"));
        mockMvc.perform(post("/api/orders/" + orderNo + "/cancel").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(510004));
    }

    @Test
    @DisplayName("未支付订单达到上限（默认 5）后再下单报 510011；取消一张即腾出名额")
    void payableOrderLimit() throws Exception {
        String first = createOrder(buyerId, monthlyPlanId);
        for (int i = 1; i < 5; i++) {
            createOrder(buyerId, monthlyPlanId);
        }

        mockMvc.perform(post("/api/orders").header("Authorization", bearer(buyerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + monthlyPlanId + "}"))
                .andExpect(jsonPath("$.code").value(510011));
        assertThat(orderRepository.countPayable(buyerId)).isEqualTo(5);
        // 上限按人计，别人不受影响
        createOrder(otherId, monthlyPlanId);

        mockMvc.perform(post("/api/orders/" + first + "/cancel").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.code").value(0));
        createOrder(buyerId, monthlyPlanId);
    }

    @Test
    @DisplayName("超时的未支付订单不占下单名额：下单前先懒惰过期再计数")
    void timedOutOrdersDoNotCountTowardLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            createOrder(buyerId, monthlyPlanId);
        }
        jdbc.update("UPDATE plan_order SET created_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 31 MINUTE) WHERE user_id = ?",
                buyerId);

        createOrder(buyerId, monthlyPlanId);
        assertThat(orderRepository.countPayable(buyerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("已履约订单的列表与单笔都带订阅分配号；未履约为 null")
    void paidOrderCarriesAssignmentNo() throws Exception {
        String paid = createOrder(buyerId, monthlyPlanId);
        String pending = createOrder(buyerId, monthlyPlanId);
        Long subscriptionId = fixtures.createSubscription(buyerId, AgentType.CLAUDE, "Claude 月付", null, null, null);
        String assignmentNo = subscriptionRepository.findById(subscriptionId).orElseThrow().getAssignmentNo();
        PlanOrder order = orderRepository.findByOrderNo(paid).orElseThrow();
        orderRepository.markPaid(paid, "pi_test", Instant.now());
        orderRepository.attachSubscription(order.getId(), subscriptionId);

        mockMvc.perform(get("/api/orders").header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.data[0].orderNo").value(pending))
                .andExpect(jsonPath("$.data[0].assignmentNo").value(nullValue()))
                .andExpect(jsonPath("$.data[1].orderNo").value(paid))
                .andExpect(jsonPath("$.data[1].assignmentNo").value(assignmentNo));
        mockMvc.perform(get("/api/orders/" + paid).header("Authorization", bearer(buyerId)))
                .andExpect(jsonPath("$.data.subscriptionId").value(subscriptionId))
                .andExpect(jsonPath("$.data.assignmentNo").value(assignmentNo));
    }
}
