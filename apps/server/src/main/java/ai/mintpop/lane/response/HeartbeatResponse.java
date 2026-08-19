package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.UserStatus;

/** 心跳结果。客户端据此决定是否断链。 */
public record HeartbeatResponse(UserStatus status) {
}
