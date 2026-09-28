package ai.mintpop.lane.dto;

import lombok.Data;
import lombok.ToString;

import java.time.Instant;

/** 机场订阅的明文领域对象。订阅链接含 token，排除出 toString 防日志外泄。 */
@Data
public class AirportSubscriptionDto {

    private Long id;

    /** 所属机场 id，引用 airport；创建后不可改 */
    private Long airportId;

    private String name;

    /** 购买该订阅所用的机场账号（如邮箱），自由文本 */
    private String account;

    /** 订阅总带宽（Mbps），创建后不可改 */
    private Integer bandwidthMbps;

    /** 订阅链接明文 */
    @ToString.Exclude
    private String subUrl;

    private String remark;

    /** 已用流量字节数（upload+download）；机场未返回额度头则为 null */
    private Long usedBytes;

    /** 总流量额度字节数；null 同上 */
    private Long totalBytes;

    /** 订阅到期时间；null 同上 */
    private Instant expiresAt;

    /** 已推送过额度告警的档位（80 或 95），服务端内部去重状态，不对外暴露 */
    private Integer trafficAlertedPct;

    /** 最近一次成功拉取订阅的时间 */
    private Instant fetchedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
