package ai.mintpop.lane.service;

import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;

public interface LinkService {

    /** 按用户身份解析出其专属链路与席位凭据 */
    LinkConfigResponse resolveLink(String subject);

    /** 心跳：告知客户端该用户当前是否仍可用 */
    HeartbeatResponse heartbeat(String subject);
}
