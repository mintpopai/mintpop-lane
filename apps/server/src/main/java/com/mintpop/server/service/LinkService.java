package com.mintpop.server.service;

import com.mintpop.server.response.HeartbeatResponse;
import com.mintpop.server.response.LinkConfigResponse;

public interface LinkService {

    /** 按员工身份解析出其专属链路与席位凭据 */
    LinkConfigResponse resolveLink(String subject);

    /** 心跳：告知客户端该员工当前是否仍可用 */
    HeartbeatResponse heartbeat(String subject);
}
