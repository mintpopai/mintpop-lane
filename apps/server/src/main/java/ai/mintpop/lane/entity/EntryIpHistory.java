package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ai.mintpop.lane.enumeration.DnsVantage;
import lombok.Data;

import java.time.Instant;

/**
 * entry_ip_history 表映射：中转入口 IP 的观测历史。
 * 服务端不带 mihomo 内核，拨不动 anytls 这类协议，测不到第一跳「通不通」，
 * 但机场的中转入口域名 TTL 只有 30 秒——就是为「被封即换 IP」准备的，服务端测得到「换没换」，
 * 入口 IP 一变，大概率意味着该入口刚被封过，是封锁事件的间接信号。
 * 本表没有敏感字段，业务层直接用它，不另设 DTO/Converter。
 */
@Data
@TableName("entry_ip_history")
public class EntryIpHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 故障域（中转入口域名） */
    private String failureDomain;

    /** 解析视角：中转入口常按运营商分线路返回不同 IP，同一故障域要按视角分别记录 */
    private DnsVantage vantage;

    /** 该视角解析到的入口 IP，多个以逗号分隔并按字典序排列（比对前先归一，避免 DNS 轮询导致的顺序抖动误报） */
    private String entryIps;

    /** 入口 IP 对应的 ASN，顺序与 entryIps 一致；单个 IP 反查失败在对应位置留空，全部失败为 NULL */
    private String asns;

    /** 观测时间（UTC），由数据库默认值 CURRENT_TIMESTAMP 维护，应用不显式写入 */
    private Instant observedAt;
}
