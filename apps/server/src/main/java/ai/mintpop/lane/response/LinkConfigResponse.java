package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 下发给客户端的链路配置。字段名与客户端 LinkConfig 逐字对应。
 * agentCredentials 为该用户全部「在期且已录凭据」的订阅；
 * 注入哪份由用户建会话时选择，客户端遇到不认识的 agentType 一律忽略。
 */
public record LinkConfigResponse(
        Map<String, Object> front,
        Map<String, Object> land,
        String expectedEgressIp,
        List<AgentCredential> agentCredentials,
        long ttlSeconds
) {

    /** 单条可用席位：订阅标识 + 套餐名 + agent 类型 + 凭据 + 止期（供客户端展示） */
    public record AgentCredential(
            Long subscriptionId,
            String name,
            AgentType agentType,
            String credential,
            Instant endsAt
    ) {
    }
}
