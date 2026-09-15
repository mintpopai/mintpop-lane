package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.enumeration.FeishuCardTemplate;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

/**
 * 换机申请的飞书通知（推给管理员的待办提醒，文案固定中文）。
 *
 * <p>尽力而为：任何异常只记日志，绝不影响申请落库——用户的申请已经提上来了，
 * 通知发不出去是运维的问题，不该反过来变成用户的失败。未配 webhook 时整体静默。
 *
 * <p>飞书卡片是单向 webhook，放不了可点的审批按钮，故只给管理端的处理入口链接。
 *
 * <p>调用点在 {@code DeviceBindingController}，而不是 {@code DeviceBindingServiceImpl.requestRebind}
 * 内部——{@code requestRebind} 是 {@code @Transactional}，只有等它返回、事务才真正提交；
 * 若把调用挪进服务内部，{@code @Async} 通知有可能在事务仍未提交时就已经跑起来、
 * 查库查不到那一行，这类 bug 只会在高并发下偶发出现，故特意留在 controller 一层。
 */
@Slf4j
@Service
public class DeviceRebindNotifyService {

    private final NotifyProperties notifyProperties;
    private final FeishuBotClient feishuBotClient;
    private final DeviceRebindRequestRepository rebindRequestRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;

    public DeviceRebindNotifyService(NotifyProperties notifyProperties,
                                     FeishuBotClient feishuBotClient,
                                     DeviceRebindRequestRepository rebindRequestRepository,
                                     SubscriptionRepository subscriptionRepository,
                                     UserRepository userRepository,
                                     UserDeviceRepository userDeviceRepository) {
        this.notifyProperties = notifyProperties;
        this.feishuBotClient = feishuBotClient;
        this.rebindRequestRepository = rebindRequestRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.userDeviceRepository = userDeviceRepository;
    }

    /** 用户提了一条换机申请（异步）。调用点在 controller，那时事务已提交，重查必见那一行 */
    @Async
    public void notifyRebindRequested(Long requestId) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            DeviceRebindRequest request = rebindRequestRepository.findById(requestId).orElse(null);
            if (request == null) {
                log.warn("换机申请通知查无此申请，跳过 requestId={}", requestId);
                return;
            }
            feishuBotClient.sendCard(FeishuCardTemplate.ORANGE,
                    "MintPop Lane 换机申请待处理", buildFields(request));
        } catch (Exception e) {
            log.warn("换机申请飞书通知失败（不影响申请本身）requestId={}", requestId, e);
        }
    }

    private LinkedHashMap<String, String> buildFields(DeviceRebindRequest request) {
        String user = userRepository.findById(request.getUserId())
                .map(UserDto::getEmail).orElse("未知用户");
        SubscriptionDto subscription = subscriptionRepository.findById(request.getSubscriptionId())
                .orElse(null);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("用户", user);
        fields.put("套餐", subscription == null ? "订阅已不存在" : subscription.getName());
        fields.put("分配号", subscription == null ? "—" : subscription.getAssignmentNo());
        fields.put("原设备", describe(request.getFromDeviceId()));
        fields.put("新设备", describe(request.getToDeviceId()));
        fields.put("理由", request.getReason() == null || request.getReason().isBlank()
                ? "（未填写）" : request.getReason());
        fields.put("申请号", request.getRequestNo());
        if (notifyProperties.getAdminUrl() != null && !notifyProperties.getAdminUrl().isBlank()) {
            fields.put("处理入口", notifyProperties.getAdminUrl() + "/device-requests");
        }
        return fields;
    }

    /** 设备压成一行：主机名是主角，系统与机型是辨认用的补充 */
    private String describe(Long deviceRowId) {
        if (deviceRowId == null) {
            return "此前未绑定";
        }
        return userDeviceRepository.findById(deviceRowId)
                .map(this::describe)
                .orElse("设备记录已不存在");
    }

    private String describe(UserDevice device) {
        String model = device.getModel() == null || device.getModel().isBlank()
                ? "" : " · " + device.getModel();
        return device.getName() + "（" + device.getOs() + model + "）";
    }
}
