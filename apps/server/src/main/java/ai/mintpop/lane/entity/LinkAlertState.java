package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * link_alert_state 表映射：记住每个「用户 × 故障域 × 运营商」当前推没推过，用于告警去重。
 * 全部字段明文，不设 DTO，与 entity 包内既有类（{@link LinkReport}、{@link LinkReportDaily} 等）一致。
 * <p>
 * {@code failureDomain} 与 {@link LinkReport} 同一套空串编码（空串表示尚未解析）。
 * {@code isp} 与 {@link LinkReportDaily#getIsp()} 同一编码——NOT NULL DEFAULT ''，进了唯一键，
 * 空串既可能表示「ASN 反查失败」，也可能表示「本行是故障域级汇总（不针对具体运营商）」，
 * 故障域级告警与运营商级告警共用本表。从 {@link LinkReport#getIsp()}（可空）读出的值写进本表前
 * 必须转换成空串，否则会在非空约束上炸。
 */
@Data
@TableName("link_alert_state")
public class LinkAlertState {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id，引用 app_user */
    private Long userId;

    /** 故障域；空串表示尚未解析（与 link_report 同一编码） */
    private String failureDomain;

    /** 运营商名；空串表示 ASN 反查失败，或本行是故障域级汇总 */
    private String isp;

    /** 当前是否处于已告警状态：true 表示已推过且尚未恢复，用于抑制重复推送 */
    private Boolean alerted;

    /** 最近一次推送时间（UTC）；从未推过为 null */
    private Instant alertedAt;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
