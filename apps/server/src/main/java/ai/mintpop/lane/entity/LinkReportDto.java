package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * link_report 表映射：客户端按 5 分钟窗口上报的第一跳健康状况聚合，保留 7 天。
 * 全部字段明文，没有密文形态，不设 DTO，业务层直接用本类。
 * <p>
 * {@code failureDomain} 在这里是空串编码（{@code ""} 表示尚未解析出故障域），
 * 与二期契约层、{@link ProxyNode#getFailureDomain()} 的 {@code null} 语义不同——
 * 转换只发生在 Service 落库/读取的那一处，本类忠实反映存储层的编码，不在这里做转换。
 */
@Data
@TableName("link_report")
public class LinkReportDto {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上报用户 id，引用 app_user */
    private Long userId;

    /** 故障域；空串表示尚未解析出故障域（不是"没有故障域"） */
    private String failureDomain;

    /** 上报窗口的起点（UTC），窗口长度 5 分钟 */
    private Instant windowStart;

    /** 窗口内的有效采样次数，成功率的分母 */
    private Integer samples;

    /** 有效采样里 alive 为真的次数，成功率的分子 */
    private Integer aliveCount;

    /** history 为空、无法判定通断的次数；刻意不计入 samples，避免把"从没测过"记成"健康" */
    private Integer noSampleCount;

    /**
     * alive 为真的那些采样的延迟中位数（毫秒）；窗口内没有 alive 样本时为 null。
     * updateStrategy = ALWAYS：默认的 NOT_NULL 策略会在「本轮没有 alive 样本、需要把上次的值清回 null」
     * 时静默跳过这一列的更新，读回的仍是上一轮的旧值。同 {@link NodeGroup#getTrafficAlertedPct()} 的处理。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer p50LatencyMs;

    /** 窗口内该故障域对应的 fallback 组 now 字段发生变化的次数，即故障转移次数 */
    private Integer failovers;

    /** 客户端实际解析到的中转入口 IP；客户端解析失败时为 null */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resolvedEntryIp;

    /** 按上报请求的来源 IP 反查到的 ASN；反查失败为 null */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceAsn;

    /** ASN 对应的运营商名；反查失败为 null */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String isp;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;
}
