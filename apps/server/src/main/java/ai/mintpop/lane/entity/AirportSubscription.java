package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * airport_subscription 表映射。持久化形态里订阅链接是密文，
 * 业务层一律用 AirportSubscriptionDto（明文），转换由 AirportSubscriptionConverter 负责。
 */
@Data
@TableName("airport_subscription")
public class AirportSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属机场 id，引用 airport；创建后不可改 */
    private Long airportId;

    private String name;

    /** 购买该订阅所用的机场账号（如邮箱），自由文本 */
    private String account;

    /** 订阅总带宽（Mbps），创建后不可改 */
    private Integer bandwidthMbps;

    /** 订阅链接的密文（链接含 token，属凭据） */
    private String subUrlCipher;

    private String remark;

    /** 已用流量字节数（upload+download），取自 subscription-userinfo 头；机场未返回该头则为 null */
    private Long trafficUsedBytes;

    /** 总流量额度字节数；null 同上 */
    private Long trafficTotalBytes;

    /** 订阅到期时间；null 同上 */
    private Instant trafficExpiresAt;

    /**
     * 已推送过额度告警的档位（80 或 95），服务端内部去重用，不对外展示；null 表示未推过。
     * updateStrategy = ALWAYS：MyBatis-Plus 默认跳过 null 字段，没有它
     * {@code TrafficAlertService} 里「用量回落（月度重置）就清档」那一步会变成静默空操作——
     * SQL 根本不带这一列，库里仍是旧档位，此后该订阅再也推不出额度告警且不报错。
     * 同 {@link ProxyNode#getEgressIp()} 与 {@link Plan#getDescription()} 的处理。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer trafficAlertedPct;

    /** 最近一次成功拉取订阅的时间 */
    private Instant fetchedAt;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    /** 由数据库 ON UPDATE 维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
