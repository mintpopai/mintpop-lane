package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;

import java.time.Instant;
import java.util.List;
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
        List<String> egressIps,
        NodeStatus status,
        String remark,
        boolean secretConfigured,
        /** 该落地节点当前的占用者姓名；未分配或非 LAND 时为 null */
        String assignedUserName,
        Instant createdAt,
        Instant updatedAt
) {
}
