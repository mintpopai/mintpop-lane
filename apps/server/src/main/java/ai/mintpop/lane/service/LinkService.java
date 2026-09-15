package ai.mintpop.lane.service;

import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;

public interface LinkService {

    /**
     * 下发链路配置。deviceId 是客户端上报的机器码（已由 DeviceId 归一化），
     * 每条席位的凭据是否下发由它与订阅的绑定关系共同决定
     */
    LinkConfigResponse resolveLink(Long userId, String deviceId);

    /** 心跳：告知客户端该用户当前是否仍可用 */
    HeartbeatResponse heartbeat(Long userId);
}
