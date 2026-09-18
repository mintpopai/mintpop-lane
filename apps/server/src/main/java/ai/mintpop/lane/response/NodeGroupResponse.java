package ai.mintpop.lane.response;

import java.time.Instant;

/**
 * 管理端的分组视图。订阅链接只回显打码形态，token 一个字符不出库。
 * 额度信息里的 trafficAlertedPct（已推送告警档位）是服务端内部去重状态，不对外暴露。
 */
public record NodeGroupResponse(
        Long id,
        String name,
        /** 打码后的订阅链接，只留 scheme 与 host */
        String subUrlMasked,
        long nodeCount,
        String remark,
        /** 已用流量字节数；机场未返回额度头则为 null */
        Long usedBytes,
        /** 总流量额度字节数；null 同上 */
        Long totalBytes,
        /** 订阅到期时间；null 同上 */
        Instant expiresAt,
        /** 最近一次成功拉取订阅的时间；从未拉取成功过则为 null */
        Instant fetchedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
