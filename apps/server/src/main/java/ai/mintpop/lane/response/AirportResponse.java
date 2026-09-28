package ai.mintpop.lane.response;

import java.time.Instant;

/** 管理端的机场视图 */
public record AirportResponse(
        Long id,
        String name,
        String websiteUrl,
        String remark,
        /** 该机场下的订阅数 */
        int subscriptionCount,
        /** 该机场全部订阅的主用总容量：各订阅 带宽/20（向下取整）之和 */
        int primaryCapacity,
        Instant createdAt,
        Instant updatedAt
) {
}
