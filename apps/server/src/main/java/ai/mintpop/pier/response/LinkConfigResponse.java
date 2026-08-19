package ai.mintpop.pier.response;

import java.util.List;
import java.util.Map;

/**
 * 下发给客户端的链路配置。字段名与客户端 LinkConfig 逐字对应。
 * 两个节点原样透传，Jackson 会把 Map 序列化成扁平 JSON 对象。
 */
public record LinkConfigResponse(
        Map<String, Object> front,
        Map<String, Object> land,
        List<String> expectedEgressIps,
        String claudeCredential,
        long ttlSeconds
) {
}
