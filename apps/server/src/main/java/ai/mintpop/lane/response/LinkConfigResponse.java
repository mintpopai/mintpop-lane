package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.DeviceBinding;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 下发给客户端的链路配置。字段名与客户端 LinkConfig 逐字对应。
 * agentCredentials 为该用户全部「在期且已录凭据」的订阅——**绑定关系不参与这一层过滤**：
 * 绑在别处的席位照列，只是凭据置空。滤掉它们，用户会看到「我明明买了，席位却凭空消失」。
 * 注入哪份由用户建会话时选择，客户端遇到不认识的 agentType 一律忽略。
 */
public record LinkConfigResponse(
        /**
         * 按用户第一跳订阅的顺位排列，每个机场订阅一组；客户端外层 fallback 按此顺序兜底，
         * 内层 url-test 在组内挑最快的节点。
         */
        List<FrontGroup> frontGroups,
        Map<String, Object> land,
        String expectedEgressIp,
        /** 落地出口 IP 对应的 IANA 时区名；未录为 null，客户端据此给终端注入 TZ */
        String egressTimezone,
        List<AgentCredential> agentCredentials,
        long ttlSeconds,
        /**
         * 本份配置中「客户端会跑的部分」的哈希（见 LinkConfigVersion）。客户端记住它，
         * 心跳返回的值不同就重拉并热加载。渲染阶段先传 null，最后用 withConfigVersion 补上。
         */
        String configVersion
) {

    public LinkConfigResponse withConfigVersion(String version) {
        return new LinkConfigResponse(frontGroups, land, expectedEgressIp, egressTimezone, agentCredentials, ttlSeconds, version);
    }

    /** 一个机场订阅下的候选前置节点 */
    public record FrontGroup(
            /**
             * 该订阅节点中出现最多的故障域，仅作上报关联键；平手取字典序最小，全未解析为 null。
             * <p>
             * 服务端不开全局 JsonInclude(NON_NULL)，这里为 null 会实打实下发成
             * {@code "failureDomain": null}，<b>客户端 DTO 必须按可空类型声明</b>，
             * 否则整份链路配置会解析失败（详见 spec §7.3）。
             */
            String failureDomain,
            /** 该故障域下的候选节点，逐个是完整 mihomo 节点定义 */
            List<Map<String, Object>> nodes
    ) {
    }

    /** 单条可用席位：订阅标识 + 分配号 + 套餐名 + agent 类型 + 凭据 + 止期（供客户端展示） */
    public record AgentCredential(
            Long subscriptionId,
            /** 分配号：客户端会话向导里用它区分同套餐的多份分配，纯展示，选中回传的仍是 subscriptionId */
            String assignmentNo,
            String name,
            AgentType agentType,
            String credential,
            /** 该凭证的 scope，空格分隔；空串表示旧式凭证，客户端不注入 scope 变量 */
            String credentialScope,
            /**
             * 席位账号所属组织的 UUID。客户端在会话启动前用它替用户记下 Fable 5 的计费同意
             * （那条记录按组织分键，键必须与 CLI 自己拉 profile 得到的组织逐字一致）；
             * 空串表示没拿到，客户端跳过预置、退回弹窗。
             */
            String credentialOrgUuid,
            /**
             * 这份席位相对本次请求那台设备的绑定关系。**凭据是否下发跟着它走**：
             * 只有 BOUND_HERE 的 credential 有值，另两种一律是空串
             */
            DeviceBinding deviceBinding,
            /** 绑在别处时，那台设备的主机名，供客户端告诉用户「它在哪」；其余情况为空串 */
            String boundDeviceName,
            /** 发起本次请求的这台设备是否已为该订阅提过换机申请且尚未被处理 */
            boolean pendingRequest,
            Instant endsAt
    ) {
    }
}
