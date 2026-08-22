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
        NodeStatus status,
        String remark,
        boolean secretConfigured,
        /** 该落地节点当前的占用者姓名；未分配或非 LAND 时为 null */
        String assignedUserName,
        /** 所属分组；手工节点为 null */
        Long groupId,
        String groupName,
        /** 订阅节点的真实 mihomo type（如 anytls）；手工节点为 null */
        String sourceType,
        Instant createdAt,
        Instant updatedAt
) {
}
