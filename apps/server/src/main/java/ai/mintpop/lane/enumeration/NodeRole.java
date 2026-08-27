package ai.mintpop.lane.enumeration;

import java.util.Set;

/** 节点在链式代理中承担的角色。角色同时决定了可用的协议集合。 */
public enum NodeRole {

    /** 第一跳：负责出国的机场节点。抗审查全靠这一跳，协议不设限 */
    FRONT(Set.of(NodeProtocol.values())),

    /**
     * 第二跳：决定最终出口 IP 的落地代理。
     * 只放行标准代理协议 —— 服务端签发凭证时要经它出站，
     * trojan/vmess 这类加密混淆协议需要 mihomo 才能连，而服务端不引入内核。
     */
    LAND(Set.of(NodeProtocol.SOCKS5, NodeProtocol.HTTP));

    private final Set<NodeProtocol> allowedProtocols;

    NodeRole(Set<NodeProtocol> allowedProtocols) {
        this.allowedProtocols = allowedProtocols;
    }

    /** 该角色是否允许使用此协议 */
    public boolean allows(NodeProtocol protocol) {
        return protocol != null && allowedProtocols.contains(protocol);
    }
}
