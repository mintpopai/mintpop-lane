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
        Map<String, Object> front,
        /**
         * 按故障域分组的前置节点。客户端据此组两层 fallback：
         * 内层每组一个 fallback（救该入口背后的落地机），外层包住各组（救入口本身）。
         * 老客户端忽略本字段、只用 front，行为与二期前逐字相同。
         */
        List<FrontGroup> frontGroups,
        Map<String, Object> land,
        String expectedEgressIp,
        /** 落地出口 IP 对应的 IANA 时区名；未录为 null，客户端据此给终端注入 TZ */
        String egressTimezone,
        List<AgentCredential> agentCredentials,
        long ttlSeconds
) {

    /** 一个故障域下的候选前置节点 */
    public record FrontGroup(
            /**
             * 故障域标识，仅作接口上的分组键与三期上报的关联键；客户端不得拿它当 mihomo 组名。
             * <p>
             * <b>可以为 null</b>，含义是「该组节点尚未解析出故障域」——手工新建的前置节点永远没有
             * failure_domain（管理端建/改节点的路径从不设它），而手工指定单节点是本期保留的运维逃生口；
             * 订阅刷新还没跑第一轮时，回填出来的节点也可能全都没有故障域。服务端不开全局
             * JsonInclude(NON_NULL)，这里为 null 会实打实下发成 {@code "failureDomain": null}，
             * <b>客户端 DTO 必须按可空类型声明</b>，否则整份链路配置会解析失败（详见 spec §7.3）。
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
