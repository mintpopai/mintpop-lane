package ai.mintpop.pier.enumeration;

import java.util.Locale;
import java.util.Set;

/**
 * 节点协议。除了决定 mihomo 的 type 字段，还决定哪些配置键属于敏感键——
 * 敏感键会被加密存储，其余键明文存在 extra_config 里。
 */
public enum NodeProtocol {

    TROJAN(Set.of("password")),
    SOCKS5(Set.of("username", "password")),
    VMESS(Set.of("uuid"));

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
