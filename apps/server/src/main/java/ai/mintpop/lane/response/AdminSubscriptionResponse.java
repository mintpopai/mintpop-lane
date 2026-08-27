package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 管理端的订阅视图。凭据只以 hasCredential 表达「有没有录」，本体一个字符都不回传。
 * 套餐信息（名称/时长/价格/币种）是分配时的快照，套餐后续改动不影响这里。
 */
public record AdminSubscriptionResponse(
        Long id,
        String assignmentNo,
        Long userId,
        /** 归属企业 id；null 表示个人订阅 */
        Long enterpriseId,
        AgentType agentType,
        Long planId,
        String name,
        Integer planDurationDays,
        BigDecimal planPrice,
        Currency planCurrency,
        Instant startsAt,
        Instant endsAt,
        /** 本次分配给用户的账号邮箱；null 表示未录 */
        String accountEmail,
        boolean hasCredential,
        /** 凭证到期时刻；null 表示没有凭证，或凭证的签发元数据被清空过（手工录入） */
        Instant credentialExpiresAt,
        /**
         * 凭证到期日是否已与订阅止期脱节（早于或晚于，均超出一天容差）。
         * true 时后台应提示重新签发——早于会让用户续期后突然断线，晚于则是超发。
         */
        boolean credentialStale,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
