package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.FeishuCardTemplate;
import ai.mintpop.lane.repository.PlanOrderRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

/**
 * 订单事件飞书通知（推给管理员的提醒，文案固定中文）。尽力而为：任何异常只记日志，
 * 绝不影响入账主流程；未配置 webhook 时整体静默。调用点已保证每单只触发一次（settlePaid 条件 UPDATE）。
 */
@Slf4j
@Service
public class OrderNotifyService {

    private final NotifyProperties notifyProperties;
    private final FeishuBotClient feishuBotClient;
    private final PlanOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    public OrderNotifyService(NotifyProperties notifyProperties, FeishuBotClient feishuBotClient,
                              PlanOrderRepository orderRepository, UserRepository userRepository,
                              SubscriptionRepository subscriptionRepository) {
        this.notifyProperties = notifyProperties;
        this.feishuBotClient = feishuBotClient;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** 支付成功、订阅已建出待开通（异步）。事务已提交后才被调用，重查必见 PAID 行 */
    @Async
    public void notifyOrderPaid(String orderNo) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            PlanOrder order = orderRepository.findByOrderNo(orderNo).orElse(null);
            if (order == null) {
                log.warn("支付成功通知查无此单，跳过 orderNo={}", orderNo);
                return;
            }
            feishuBotClient.sendCard(FeishuCardTemplate.GREEN, "MintPop Lane 新订单已支付，待开通", buildFields(order));
        } catch (Exception e) {
            log.warn("支付成功飞书通知失败（不影响入账）orderNo={}", orderNo, e);
        }
    }

    private LinkedHashMap<String, String> buildFields(PlanOrder order) {
        String buyer = userRepository.findById(order.getUserId()).map(UserDto::getEmail).orElse("未知买家");
        String assignmentNo = subscriptionRepository.findById(order.getSubscriptionId())
                .map(SubscriptionDto::getAssignmentNo).orElse("未建出");
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("买家", buyer);
        fields.put("套餐", order.getName());
        fields.put("类型", order.getAgentType().name());
        fields.put("时长", order.getPlanDurationDays() + " 天");
        fields.put("金额", order.getPlanPrice().toPlainString() + " " + order.getPlanCurrency().name());
        fields.put("订单号", order.getOrderNo());
        fields.put("分配号", assignmentNo);
        return fields;
    }
}
