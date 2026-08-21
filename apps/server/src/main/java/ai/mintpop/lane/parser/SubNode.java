package ai.mintpop.lane.parser;

import java.util.Map;

/**
 * 订阅里解析出的一个节点。
 * params 是「去掉 name 的整份 mihomo 参数」（含 type/server/port），入库时整体加密。
 */
public record SubNode(
        String sourceName,
        String sourceType,
        String serverAddr,
        int port,
        Map<String, Object> params,
        boolean suspectedInfo
) {
}
