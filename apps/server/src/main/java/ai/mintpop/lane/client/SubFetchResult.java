package ai.mintpop.lane.client;

import java.time.Instant;

/**
 * 订阅拉取结果：响应体 + 机场在 content-disposition / subscription-userinfo 头里给的元信息。
 * airportName 与三个额度字段**都可能为 null**——不是所有机场都返回这些头，缺了不影响导入。
 */
public record SubFetchResult(String body, String airportName, Long usedBytes, Long totalBytes, Instant expiresAt) {

    /** 已用占比（0-100）；额度信息不全时返回 null */
    public Integer usedPercent() {
        if (usedBytes == null || totalBytes == null || totalBytes <= 0) {
            return null;
        }
        return (int) Math.min(100, usedBytes * 100 / totalBytes);
    }
}
