package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;

import java.time.Instant;
import java.util.List;

/** 管理端的用户视图。 */
public record AdminUserResponse(
        Long id,
        String subject,
        String email,
        UserRole role,
        UserStatus status,
        Long frontNodeId,
        String frontNodeName,
        /** 该用户当前分配到的一组前置节点（按故障域分桶后的完整集合，不止 front_node_id 那个「主」节点） */
        List<FrontNodeBrief> frontNodes,
        /**
         * frontNodes 覆盖的故障域个数（去重后统计 failureDomain 非空的节点）。
         * 等于 1 说明该用户名下所有前置节点共用同一台中转入口机——入口一挂全部失效，
         * fallback 是假冗余，需要采购第二家机场；管理端据此常驻警示，不是一次性通知。
         */
        int failureDomainCount,
        /** 该用户的第一跳机场订阅列表，按顺位排列；未分配为空列表 */
        List<FrontSubscriptionBrief> frontSubscriptions,
        Long landNodeId,
        String landNodeName,
        /** 该用户的期望出口 IP，取自其落地节点；未分配或落地未填出口时为 null */
        String egressIp,
        /** 在期订阅摘要，供列表一眼看出这个人开了什么、到什么时候 */
        List<ActiveSubscriptionBrief> activeSubscriptions,
        /** 备注，管理员自用说明；没写时为 null。只出现在管理端，不下发给用户侧 */
        String remark,
        Instant createdAt,
        Instant updatedAt
) {

    /** 在期订阅摘要，供列表一眼看出这个人开了什么、到什么时候 */
    public record ActiveSubscriptionBrief(Long id, String name, AgentType agentType, Instant endsAt) {
    }

    /** 前置节点摘要：管理端按 failureDomain 分组展示，null 表示该节点尚未解析出故障域 */
    public record FrontNodeBrief(Long id, String name, String failureDomain) {
    }
}
