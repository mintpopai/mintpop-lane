package ai.mintpop.lane.response;

import java.time.Instant;

/** 桌面端最新版本的拉取状态（管理端展示）；进程内保存，服务端重启后重新拉取 */
public record ClientVersionStatusResponse(
        /** 当前认定的最新版本，如 1.2.0；从未拉到过为 null（此时只拦不带版本头的请求） */
        String latest,
        /** 最近一次拉取成功的时刻；从未成功为 null */
        Instant fetchedAt,
        /** 最近一次尝试拉取的时刻；还没尝试过为 null */
        Instant lastAttemptAt,
        /** 最近一次尝试是否失败；失败时 latest 沿用上一次拉到的值 */
        boolean lastAttemptFailed,
        /** 读取的更新清单地址 */
        String manifestUrl
) {
}
