package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;

import java.time.Instant;
import java.util.Map;

/**
 * 管理端的节点视图。
 * 敏感键只以 secretConfigured 表达「有没有配」，密码本身一个字符都不回传。
 */
public record AdminNodeResponse(
        Long id,
        String name,
        NodeRole role,
        NodeProtocol protocol,
        String serverAddr,
        Integer port,
        Map<String, Object> extraConfig,
        /** 出口 IP，仅 LAND 节点有值；未填为 null */
        String egressIp,
        /** 落地出口 IP 对应的 IANA 时区名，仅 LAND 节点有值；未填为 null */
        String egressTimezone,
        NodeStatus status,
        String remark,
        boolean secretConfigured,
        /** 落地节点容量（最多可绑定的用户数）；非 LAND 为 null */
        Integer capacity,
        /** 该落地节点当前绑定的用户数；非 LAND 为 null */
        Long assignedUserCount,
        /** 所属机场订阅；手工节点为 null */
        Long airportSubscriptionId,
        String airportSubscriptionName,
        /** 订阅节点的真实 mihomo type（如 anytls）；手工节点为 null */
        String sourceType,
        /** 故障域：节点域名 CNAME 链的终点。仅对 FRONT 节点有意义；null 表示尚未解析或解析失败 */
        String failureDomain,
        Instant createdAt,
        Instant updatedAt
) {
}
