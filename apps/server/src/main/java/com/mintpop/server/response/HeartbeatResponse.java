package com.mintpop.server.response;

import com.mintpop.server.enumeration.EmployeeStatus;

/** 心跳结果。客户端据此决定是否断链。 */
public record HeartbeatResponse(EmployeeStatus status) {
}
