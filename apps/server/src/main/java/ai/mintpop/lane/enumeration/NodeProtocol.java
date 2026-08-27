package ai.mintpop.lane.enumeration;

import java.util.Locale;
import java.util.Set;

/**
 * 节点协议。除了决定 mihomo 的 type 字段，还决定哪些配置键属于敏感键——
 * 敏感键会被加密存储，其余键明文存在 extra_config 里。
 */
public enum NodeProtocol {

    TROJAN(Set.of("password")),
    SOCKS5(Set.of("username", "password")),
    /**
     * 标准 HTTP 代理。与 SOCKS5 并列为落地节点可用的两种协议——
     * 服务端签发凭证时要经落地出口出站，只有标准代理协议连得上。
     */
    HTTP(Set.of("username", "password")),
    VMESS(Set.of("uuid")),
    /**
     * 订阅导入的通用透传协议：整份 mihomo 参数（含 type/server/port）作为敏感配置整体加密，
     * 不存在「按键区分敏感」的概念，故 secretKeys 为空集。只能经订阅导入产生，不可手工新建。
     * 注意它的 mihomoType() 没有意义——下发时 ProxyNodeDto.toMihomoNode() 走专门分支，不会调用它。
     */
    MIHOMO(Set.of());

    private final Set<String> secretKeys;

    NodeProtocol(Set<String> secretKeys) {
        this.secretKeys = secretKeys;
    }

    /** 该协议下需要加密存储的配置键 */
    public Set<String> secretKeys() {
        return secretKeys;
    }

    /** mihomo 配置里 type 字段的取值，是枚举名的小写形式 */
    public String mihomoType() {
        return name().toLowerCase(Locale.ROOT);
    }
}
