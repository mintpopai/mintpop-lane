package com.mintpop.server.response;

import com.mintpop.server.enumeration.NodeProtocol;
import com.mintpop.server.enumeration.NodeRole;
import com.mintpop.server.enumeration.NodeStatus;

import java.time.LocalDateTime;
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
