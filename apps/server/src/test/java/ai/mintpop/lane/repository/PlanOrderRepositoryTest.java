package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import ai.mintpop.lane.enumeration.OrderStatus;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlanOrderRepositoryTest extends MysqlTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PlanOrderRepository orderRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        userId = fixtures.createUser("logto-buyer", null, null);
    }

    private PlanOrder pending(String orderNo) {
        PlanOrder o = new PlanOrder();
        o.setOrderNo(orderNo);
        o.setUserId(userId);
        o.setPlanId(1L);
        o.setName("Claude 月付");
        o.setAgentType(AgentType.CLAUDE);
        o.setPlanDurationDays(30);
        o.setPlanPrice(new BigDecimal("99.99"));
        o.setPlanCurrency(Currency.USD);
        o.setAmountMinor(9999L);
        o.setStatus(OrderStatus.PENDING);
        orderRepository.create(o);
        return o;
    }

    @Test
    @DisplayName("入账只对可入账来源生效，重放返回 false；取消与过期后到账仍入账")
    void markPaidTransitions() {
        pending("LN1");
        assertThat(orderRepository.markPaid("LN1", "pi_1", Instant.now())).isTrue();
        assertThat(orderRepository.markPaid("LN1", "pi_1", Instant.now())).isFalse();
        PlanOrder paid = orderRepository.findByOrderNo("LN1").orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaymentTradeNo()).isEqualTo("pi_1");
        assertThat(paid.getPaymentProvider()).isEqualTo("stripe");

        pending("LN2");
        assertThat(orderRepository.markCancelled("LN2")).isTrue();
        assertThat(orderRepository.markPaid("LN2", "pi_2", Instant.now())).isTrue();

        pending("LN3");
        assertThat(orderRepository.markExpired("LN3")).isTrue();
        assertThat(orderRepository.markPaid("LN3", "pi_3", Instant.now())).isTrue();
    }

    @Test
    @DisplayName("取消/过期/失败只能从待支付或支付失败出发，已支付的单一律不动")
    void otherTransitionsGuardSource() {
        pending("LN1");
        orderRepository.markPaid("LN1", "pi_1", Instant.now());
        assertThat(orderRepository.markCancelled("LN1")).isFalse();
        assertThat(orderRepository.markExpired("LN1")).isFalse();
        assertThat(orderRepository.markFailed("LN1")).isFalse();

        pending("LN2");
        assertThat(orderRepository.markFailed("LN2")).isTrue();
        assertThat(orderRepository.markFailed("LN2")).isFalse();
        assertThat(orderRepository.markCancelled("LN2")).isTrue();
    }

    @Test
    @DisplayName("落交易号只在首次生效：已有交易号时第二次落号返回 false 且不覆盖（防并发发起支付产生孤儿 intent）")
    void attachPaymentIntentOnlyOnce() {
        PlanOrder order = pending("LN1");
        assertThat(orderRepository.attachPaymentIntent(order.getId(), "stripe", "pi_winner")).isTrue();
        assertThat(orderRepository.attachPaymentIntent(order.getId(), "stripe", "pi_loser")).isFalse();
        assertThat(orderRepository.findByOrderNo("LN1").orElseThrow().getPaymentTradeNo()).isEqualTo("pi_winner");
    }

    @Test
    @DisplayName("按用户查订单按创建倒序并受 limit 限制；超时查询只取可支付状态")
    void listAndTimedOut() {
        pending("LN1");
        pending("LN2");
        orderRepository.markPaid("LN2", "pi", Instant.now());
        assertThat(orderRepository.findByUserId(userId, 10)).extracting(PlanOrder::getOrderNo)
                .containsExactly("LN2", "LN1");
        assertThat(orderRepository.findByUserId(userId, 1)).hasSize(1);
        assertThat(orderRepository.findTimedOut(userId, Instant.now().plusSeconds(60)))
                .extracting(PlanOrder::getOrderNo).containsExactly("LN1");
        assertThat(orderRepository.findTimedOut(userId, Instant.now().minusSeconds(3600))).isEmpty();
    }
}
