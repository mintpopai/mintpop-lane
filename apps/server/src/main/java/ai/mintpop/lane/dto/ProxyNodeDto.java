package ai.mintpop.lane.dto;

import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;
import java.util.LinkedHashMap;
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

    /** 出口 IP，仅 LAND 有值；null 表示未填 */
    private String egressIp;

    /** 落地出口 IP 对应的 IANA 时区名，仅 LAND 有值；null 表示未填 */
    private String egressTimezone;

    /** 落地节点容量：最多可绑定的用户数，仅 LAND 有意义；新建时 null 走数据库默认值 10 */
    private Integer capacity;

    private NodeStatus status = NodeStatus.ENABLED;

    private String remark;

    /** 所属机场订阅 id；NULL 表示手工节点 */
    private Long airportSubscriptionId;

    /** 订阅里的原始节点名，重新拉取时据此匹配；手工节点为 NULL */
    private String sourceName;

    /** 订阅节点的真实 mihomo type（如 anytls），仅供展示；手工节点为 NULL */
    private String sourceType;

    /** 故障域：该节点域名解析链的终点（CNAME 末端；无 CNAME 则为域名本身）。NULL 表示尚未解析或解析失败 */
    private String failureDomain;

    /** 故障域最近一次解析成功的时间（UTC）；NULL 表示从未解析成功 */
    private Instant failureDomainCheckedAt;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 组装成一个 mihomo 节点配置，不做任何参数覆盖。
     * 既有调用点（如落地节点下发）继续用这个签名，无需感知覆盖表的存在。
     */
    public Map<String, Object> toMihomoNode() {
        return toMihomoNode(Map.of());
    }

    /**
     * 组装成一个 mihomo 节点配置，并按 {@code sourceType} 合并保活参数覆盖表。
     * 不含 name 与 dialer-proxy：这两项由客户端强制覆盖，服务端下发了也会被改掉。
     *
     * @param tuning 按 mihomo type 分派的覆盖表（{@link ai.mintpop.lane.config.FrontTuningProperties}），
     *               只应对前置节点传非空表；表里没有本节点的 sourceType 时相当于不传
     */
    public Map<String, Object> toMihomoNode(Map<String, Object> tuning) {
        // MIHOMO（订阅导入）节点：整份参数都在 secret 里（含 type/server/port），原样透传；
        // 列上的 serverAddr/port 只是展示用副本，这里不读，避免两处数据不一致时下发错值
        if (protocol == NodeProtocol.MIHOMO) {
            Map<String, Object> node = new LinkedHashMap<>(secret == null ? Map.of() : secret);
            // 后放即覆盖：订阅带来的默认值不如我们的调优结论权威
            if (tuning != null) {
                node.putAll(tuning);
            }
            return node;
        }
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
