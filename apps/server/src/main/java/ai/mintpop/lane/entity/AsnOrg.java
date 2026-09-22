package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * asn_org 表映射：ASN 到运营商展示名的映射，几十行量级。
 * <p>
 * 运营商维度以 ASN 做键（{@link LinkReport#getSourceAsn()}、{@link LinkReportDaily#getAsn()}、
 * {@link LinkAlertState#getAsn()}），名字<b>只做展示</b>：上游给的组织名是自由文本
 * （同一家运营商可能今天叫 {@code China Telecom}、明天叫 {@code CHINANET-BACKBONE}），
 * 拿它做键会让同一个运营商在矩阵里裂成两列、告警去重也跟着裂。
 * <p>
 * 主键是字符串 {@code asn} 而不是自增 id：本表就是一张按 ASN 查的字典，
 * 自增 id 只会多一层间接。故 {@link IdType#INPUT}——主键由应用给出，数据库不生成。
 */
@Data
@TableName("asn_org")
public class AsnOrg {

    /** ASN，形如 AS4134，与 link_report.source_asn 同一写法 */
    @TableId(type = IdType.INPUT)
    private String asn;

    /** 首次反查到该 ASN 时上游给的组织名，只做展示；最长 64 字符，超长由服务端截断 */
    private String orgName;

    /** 首次见到该 ASN 的时间（UTC），由调用方传入——取「现在」是调用方注入的 Clock 的事，不在仓储里取 */
    private Instant firstSeenAt;
}
