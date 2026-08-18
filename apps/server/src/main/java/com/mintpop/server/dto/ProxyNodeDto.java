package com.mintpop.server.dto;

import com.mintpop.server.enumeration.NodeProtocol;
import com.mintpop.server.enumeration.NodeRole;
import com.mintpop.server.enumeration.NodeStatus;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点的明文领域对象。Service 层只见这个类型，密文只存在于 entity 与 converter 之间。
 */
@Data
public class ProxyNodeDto {

    private Long id;

    private String name;

    private NodeRole role;

    private NodeProtocol protocol;

    private String serverAddr;

    private Integer port;

    /** 非敏感的 mihomo 透传键 */
    private Map<String, Object> extraConfig = Map.of();

    /** 敏感键的明文，如 {password: xxx}。排除出 toString，避免凭据随日志外泄 */
    @ToString.Exclude
    private Map<String, Object> secret = Map.of();

    /** 出口 IP 集合，仅 LAND 有值 */
    private List<String> egressIps = List.of();

    private NodeStatus status = NodeStatus.ENABLED;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 组装成一个 mihomo 节点配置。
     * 不含 name 与 dialer-proxy：这两项由客户端强制覆盖，服务端下发了也会被改掉。
     */
    public Map<String, Object> toMihomoNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", protocol.mihomoType());
        node.put("server", serverAddr);
        node.put("port", port);
        if (extraConfig != null) {
            node.putAll(extraConfig);
        }
        if (secret != null) {
            node.putAll(secret);
        }
        return node;
    }
}
