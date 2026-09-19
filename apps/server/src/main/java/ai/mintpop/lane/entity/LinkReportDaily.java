package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * link_report_daily 表映射：由归档任务从 link_report 压出的按天聚合，保留 90 天。
 * 全部字段明文，不设 DTO。
 * <p>
 * 刻意不带 p50 延迟——中位数不可跨窗口相加，硬算会得到一个没有意义的数。
 * {@code failureDomain}/{@code isp} 与 {@link LinkReport} 同一套空串编码：
 * 空串表示"尚未解析"/"ASN 反查失败"，不是 null，否则可空列进了唯一键会去不了重。
 */
@Data
@TableName("link_report_daily")
public class LinkReportDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id，引用 app_user */
    private Long userId;

    /** 故障域；空串表示尚未解析 */
    private String failureDomain;

    /** 运营商名；空串表示 ASN 反查失败 */
    private String isp;

    /** 统计日（UTC 日历日） */
    private LocalDate statDate;

    /** 当日有效采样总数 */
    private Long samples;

    /** 当日 alive 总数 */
    private Long aliveCount;

    /** 当日无法判定的次数 */
    private Long noSampleCount;

    /** 当日故障转移总次数 */
    private Long failovers;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;
}
