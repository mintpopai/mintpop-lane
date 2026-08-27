package ai.mintpop.lane.dto;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

/** 订阅的明文领域对象。凭据在这里是明文，密文只存在于 entity 与 converter 之间。 */
@Data
public class SubscriptionDto {

    private Long id;

    /**
     * 分配号：给用户看的分配标识，10 位 Crockford Base32 大写短码。
     * 用户界面与工单里用它指认「是哪一次分配」，程序内部引用仍走自增 id。
     */
    private String assignmentNo;

    private Long userId;

    /** 归属企业 id；NULL 表示个人订阅 */
    private Long enterpriseId;

    private AgentType agentType;

    /** 所选套餐 id。弱引用，套餐硬删后允许悬空，历史呈现以快照字段为准 */
    private Long planId;

    /** 用户可见的套餐名，分配时从所选套餐快照 */
    private String name;

    /** 套餐时长快照（天）：止期 = 起期 + 本值 */
    private Integer planDurationDays;

    /** 套餐价格快照 */
    private BigDecimal planPrice;

    /** 套餐币种快照 */
    private Currency planCurrency;

    private Instant startsAt;

    private Instant endsAt;

    /** 本次分配给用户的账号邮箱，一律小写；null 表示未录 */
    private String accountEmail;

    /** 席位凭据（明文）。排除出 toString，避免凭据随日志外泄 */
    @ToString.Exclude
    private String credential;

    /**
     * 凭证到期时刻；null 表示没有凭证或是签发元数据被清空过的旧式/手工凭证。
     * 只读方向映射（{@code SubscriptionConverter#toEntity} 不回写它）——
     * 常规更新走的 update() SQL 本就不含这一列，写入靠专用的
     * updateCredential/clearCredentialMetadata，这里只是给管理端展示与
     * 到期一致性判定用的读出口，不参与常规更新覆盖。
     */
    private Instant credentialExpiresAt;

    private String remark;

    private Instant createdAt;

    private Instant updatedAt;

    /** 在期判定：起含止不含。判定点在拉链路与心跳里，纯查询、无定时任务 */
    public boolean isActiveAt(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
