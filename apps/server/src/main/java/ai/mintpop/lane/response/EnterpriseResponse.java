package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;

import java.time.Instant;
import java.util.List;

/** 管理端的企业视图 */
public record EnterpriseResponse(
        Long id,
        String name,
        /** 企业域名，小写 */
        String domain,
        /** 本企业支持的 agent 类型 */
        List<AgentType> agentTypes,
        /** 启用状态：false 表示停用但保留 */
        Boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
