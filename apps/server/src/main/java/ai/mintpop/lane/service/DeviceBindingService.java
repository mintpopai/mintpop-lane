package ai.mintpop.lane.service;

import ai.mintpop.lane.request.DeviceBindRequest;
import ai.mintpop.lane.request.DeviceRebindCreateRequest;

/**
 * 用户侧的设备绑定：绑到本机、提换机申请。管理员侧的裁决与解绑见 AdminDeviceService。
 *
 * <p>两个方法的 {@code deviceId} 参数都必须是已经过 {@link ai.mintpop.lane.util.DeviceId#normalize}
 * 归一化的机器码——调用方（controller）负责这一步，本服务不重复校验形状。
 */
public interface DeviceBindingService {

    /** 把订阅绑到这台设备。已绑在本机按幂等成功处理；绑在别处抛 SUBSCRIPTION_BOUND_ELSEWHERE */
    void bind(Long userId, Long subscriptionId, String deviceId, DeviceBindRequest body);

    /** 提一条换机申请，返回新建申请的 id。只有「绑在别处」才谈得上申请 */
    Long requestRebind(Long userId, Long subscriptionId, String deviceId,
                       DeviceRebindCreateRequest body);
}
