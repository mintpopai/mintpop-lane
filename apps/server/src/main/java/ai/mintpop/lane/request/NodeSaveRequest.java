package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.util.Map;

/** 新建或更新节点的入参。 */
@Data
public class NodeSaveRequest {

    @NotBlank(message = "节点名不能为空")
    @Size(max = 64, message = "节点名最长 64 个字符")
    private String name;

    @NotNull(message = "节点角色不能为空")
    private NodeRole role;

    @NotNull(message = "协议不能为空")
    private NodeProtocol protocol;

    @NotBlank(message = "节点地址不能为空")
    private String serverAddr;

    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口必须在 1 到 65535 之间")
    @Max(value = 65535, message = "端口必须在 1 到 65535 之间")
    private Integer port;

    /** 非敏感的 mihomo 透传键 */
    private Map<String, Object> extraConfig = Map.of();

    /**
     * 敏感键明文。更新时留空表示沿用原值，不清空。
     * 排除出 toString，与 {@code ProxyNodeDto.secret} / {@code SubscriptionDto.credential}
     * 保持一致：目前全仓没有任何地方打印这个入参对象，但一旦日后有人加一行
     * {@code log.info("request={}", request)} 或某个异常把它塞进消息，
     * 没有这个注解密码就会原样进日志。
     */
    @ToString.Exclude
    private Map<String, Object> secret = Map.of();

    /** 出口 IP，仅 LAND 需要；null 或空白表示未填 */
    @Size(max = 64, message = "出口 IP 最长 64 个字符")
    private String egressIp;

    /** 落地出口时区（IANA 时区名），仅 LAND 需要；null 或空白表示未填 */
    @Size(max = 64, message = "出口时区最长 64 个字符")
    private String egressTimezone;

    @NotNull(message = "状态不能为空")
    private NodeStatus status = NodeStatus.ENABLED;

    @Size(max = 255, message = "备注最长 255 个字符")
    private String remark;
}
