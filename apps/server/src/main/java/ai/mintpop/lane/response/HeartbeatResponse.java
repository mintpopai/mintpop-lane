package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.LinkStatus;

/**
 * 心跳结果。status 决定断链方式（EXPIRED 保留登录态，SUSPENDED/REVOKED 回登录页）；
 * configVersion 是服务端此刻按用户分配与库里节点现算的配置版本，客户端与自己手里那份比对，不同就立刻重拉并热加载。
 * 渲染不出配置（未分配、订阅无节点、无落地）时为 null，客户端不动作。
 */
public record HeartbeatResponse(LinkStatus status, String configVersion) {
}
