package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import ai.mintpop.lane.enumeration.FeishuCardTemplate;
import ai.mintpop.lane.enumeration.OrderStatus;
import ai.mintpop.lane.repository.PlanOrderRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNotifyServiceTest {

    @Mock private FeishuBotClient feishuBotClient;
    @Mock private PlanOrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    private OrderNotifyService service(boolean configured) {
        NotifyProperties props = new NotifyProperties();
        props.setWebhookUrl(configured ? "https://open.feishu.cn/open-apis/bot/v2/hook/x" : "");
        return new OrderNotifyService(props, feishuBotClient, orderRepository, userRepository, subscriptionRepository);
    }

    private PlanOrder paidOrder() {
        PlanOrder o = new PlanOrder();
        o.setId(7L);
        o.setOrderNo("LN20260915083005123456");
        o.setUserId(42L);
        o.setName("Claude 月付");
        o.setAgentType(AgentType.CLAUDE);
        o.setPlanDurationDays(30);
        o.setPlanPrice(new BigDecimal("99.99"));
        o.setPlanCurrency(Currency.USD);
        o.setAmountMinor(9999L);
        o.setStatus(OrderStatus.PAID);
        o.setPaymentTradeNo("pi_1");
        o.setSubscriptionId(9L);
        return o;
    }

    @Test
    @DisplayName("绿色卡片：买家邮箱、套餐、类型、时长、金额、订单号、分配号")
    void sendsCardWithFields() {
        when(orderRepository.findByOrderNo("LN20260915083005123456")).thenReturn(Optional.of(paidOrder()));
        UserDto user = new UserDto();
        user.setEmail("zhang@acme.com");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        SubscriptionDto s = new SubscriptionDto();
        s.setAssignmentNo("7K3M9QX2FT");
        when(subscriptionRepository.findById(9L)).thenReturn(Optional.of(s));

        service(true).notifyOrderPaid("LN20260915083005123456");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkedHashMap<String, String>> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(feishuBotClient).sendCard(eq(FeishuCardTemplate.GREEN), eq("MintPop Lane 新订单已支付，待开通"), captor.capture());
        LinkedHashMap<String, String> fields = captor.getValue();
        assertThat(fields.keySet()).containsExactly("买家", "套餐", "类型", "时长", "金额", "订单号", "分配号");
        assertThat(fields).containsEntry("买家", "zhang@acme.com")
                .containsEntry("套餐", "Claude 月付")
                .containsEntry("类型", "CLAUDE")
                .containsEntry("时长", "30 天")
                .containsEntry("金额", "99.99 USD")
                .containsEntry("订单号", "LN20260915083005123456")
                .containsEntry("分配号", "7K3M9QX2FT");
    }

    @Test
    @DisplayName("未配置 webhook 时静默；发送异常只记日志不上抛")
    void silentWhenUnconfiguredAndSwallowsErrors() {
        service(false).notifyOrderPaid("LN1");
        verify(feishuBotClient, never()).sendCard(any(), anyString(), any());

        when(orderRepository.findByOrderNo("LN20260915083005123456")).thenReturn(Optional.of(paidOrder()));
        when(userRepository.findById(42L)).thenReturn(Optional.empty());
        when(subscriptionRepository.findById(9L)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("飞书机器人返回异常")).when(feishuBotClient).sendCard(any(), anyString(), any());
        service(true).notifyOrderPaid("LN20260915083005123456");
    }
}
