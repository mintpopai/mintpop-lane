package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.LinkStatus;

/** 心跳结果。客户端据此决定断链方式（EXPIRED 保留登录态，SUSPENDED/REVOKED 回登录页）。 */
public record HeartbeatResponse(LinkStatus status) {
}
