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
public class LinkReport {

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
     * <p>
     * ⚠️ <b>这一列（以及下面三个可空列）能被清回 null，靠的不是下面那个注解，而是
     * {@code LinkReportMapper.upsertWindow} 的原生 SQL 里显式列出了 {@code p50_latency_ms = VALUES(p50_latency_ms)}。</b>
     * {@code updateStrategy} 只作用于 MyBatis-Plus 自动生成的 {@code updateById} / {@code update(wrapper)}，
     * 手写的 {@code INSERT ... ON DUPLICATE KEY UPDATE} 完全绕过它——Task 1 实施时做过对照实验：
     * 去掉注解，{@code p50CanBeClearedBackToNull} 照样通过；只有改坏 Mapper 里那行才会变红。
     * <p>
     * 注解仍然保留，是为了将来有人改用标准 update 调用时有一层防御（那条路径上它才真正生效，
     * 形态同 {@link AirportSubscription#getTrafficAlertedPct()}——一期就栽在那里，配额告警永久失声）。
     * <b>新增任何写路径时，不要以为可空字段的清空已经由注解保证了，去看那条 SQL。</b>
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer p50LatencyMs;

    /** 窗口内该故障域对应的 fallback 组 now 字段发生变化的次数，即故障转移次数 */
    private Integer failovers;

    /** 客户端实际解析到的中转入口 IP；客户端解析失败时为 null */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resolvedEntryIp;

    /**
     * 按上报请求的来源 IP 反查到的 ASN（形如 AS4134）；反查失败为 null。
     * 运营商维度以它做键——展示名是上游给的自由文本、随时漂移，只能做展示，另存 {@link AsnOrg}。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceAsn;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;
}
