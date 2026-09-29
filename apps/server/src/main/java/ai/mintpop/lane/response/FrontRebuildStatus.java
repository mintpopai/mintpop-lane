package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.FrontRebuildPhase;

import java.time.Instant;

/** 最近一次全体重算的状态；进程内保存，服务端重启即回到 IDLE */
public record FrontRebuildStatus(
        FrontRebuildPhase phase,
        Instant startedAt,
        Instant finishedAt,
        /** 成功时分配的用户数；其它阶段 null */
        Integer userCount,
        /** 成功时参与的候选订阅数；其它阶段 null */
        Integer subscriptionCount,
        /** FAILED 时的原因；其它阶段 null */
        String error
) {
    public static FrontRebuildStatus idle() {
        return new FrontRebuildStatus(FrontRebuildPhase.IDLE, null, null, null, null, null);
    }
}
