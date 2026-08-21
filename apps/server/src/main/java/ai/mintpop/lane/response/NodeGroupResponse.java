package ai.mintpop.lane.response;

import java.time.Instant;

/** 管理端的分组视图。订阅链接只回显打码形态，token 一个字符不出库。 */
public record NodeGroupResponse(
        Long id,
        String name,
        /** 打码后的订阅链接，只留 scheme 与 host */
        String subUrlMasked,
        long nodeCount,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
