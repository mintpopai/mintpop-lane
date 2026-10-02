package ai.mintpop.lane.response;

import java.time.Instant;

/** 管理端的机场视图 */
public record AirportResponse(
        Long id,
        String name,
        String websiteUrl,
        String remark,
        /** 是否主用机场：false 时只当备用 */
        boolean primaryEnabled,
        /** 该机场下的订阅数 */
        int subscriptionCount,
        /** 该机场全部订阅当前主用人数之和 */
        int primaryUsed,
        /** 该机场全部订阅的主用总容量：各订阅 带宽 ÷ 每人带宽（全局配置，向下取整）之和；非主用机场恒为 0 */
        int primaryCapacity,
        Instant createdAt,
        Instant updatedAt
) {
}
