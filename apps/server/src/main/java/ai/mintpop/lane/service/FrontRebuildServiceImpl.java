package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.FrontRebuildPhase;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.response.FrontRebuildPreview;
import ai.mintpop.lane.response.FrontRebuildStatus;
import ai.mintpop.lane.service.FrontRebuildRunner.RebuildResult;
import ai.mintpop.lane.service.SubRefreshService.RefreshOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全体重算的编排：互斥 → 重拉全部订阅（外呼，事务外）→ 任一订阅失败即中止 → 事务内重排写回 → 状态与飞书。
 * 状态在进程内（服务端单实例部署），重启丢失就回到 IDLE，管理员重按一次按钮即可。
 */
@Slf4j
@Service
public class FrontRebuildServiceImpl implements FrontRebuildService {

    private final SubRefreshService subRefreshService;
    private final FrontRebuildRunner runner;
    private final NodeNotifyService nodeNotifyService;
    private final Executor executor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<FrontRebuildStatus> status = new AtomicReference<>(FrontRebuildStatus.idle());

    public FrontRebuildServiceImpl(SubRefreshService subRefreshService, FrontRebuildRunner runner,
                                   NodeNotifyService nodeNotifyService,
                                   @Qualifier("frontRebuildExecutor") Executor executor, Clock clock) {
        this.subRefreshService = subRefreshService;
        this.runner = runner;
        this.nodeNotifyService = nodeNotifyService;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public FrontRebuildStatus status() {
        return status.get();
    }

    @Override
    public FrontRebuildPreview preview(FrontSettings settings, boolean keepManual) {
        return runner.preview(settings, keepManual);
    }

    @Override
    public void start(boolean keepManual) {
        if (!running.compareAndSet(false, true)) {
            throw new BizException(BizCodeEnum.FRONT_REBUILD_RUNNING);
        }
        Instant startedAt = clock.instant();
        status.set(new FrontRebuildStatus(FrontRebuildPhase.RUNNING, startedAt, null, null, null, null));
        try {
            executor.execute(() -> run(startedAt, keepManual));
        } catch (RuntimeException e) {
            // 提交失败（如线程池拒绝）：任务没跑起来，必须释放互斥并把状态落成 FAILED，否则永远卡在 RUNNING
            log.error("全体重算任务提交失败", e);
            status.set(new FrontRebuildStatus(FrontRebuildPhase.FAILED, startedAt, clock.instant(), null, null,
                    "内部错误：" + e.getClass().getSimpleName()));
            running.set(false);
            throw e;
        }
    }

    private void run(Instant startedAt, boolean keepManual) {
        try {
            RefreshOutcome refresh = subRefreshService.refreshAllNow();
            if (!refresh.failedSubscriptionNames().isEmpty()) {
                throw new BizException(BizCodeEnum.FRONT_REBUILD_FETCH_FAILED,
                        String.join("、", refresh.failedSubscriptionNames()));
            }
            RebuildResult result = runner.applyAll(keepManual);
            status.set(new FrontRebuildStatus(FrontRebuildPhase.SUCCEEDED, startedAt, clock.instant(),
                    result.userCount(), result.subscriptionCount(), null, result.keptManualCount()));
            nodeNotifyService.notifyFrontRebuildFinished(result.userCount(), result.subscriptionCount(),
                    result.keptManualCount());
        } catch (BizException e) {
            fail(startedAt, e.getMessage());
        } catch (RuntimeException e) {
            log.error("全体重算出现未预期异常", e);
            fail(startedAt, "内部错误：" + e.getClass().getSimpleName());
        } finally {
            // Error 之类逃出上面 catch 时状态还停在 RUNNING，兜底落成 FAILED，避免前端永远转圈
            if (status.get().phase() == FrontRebuildPhase.RUNNING) {
                status.set(new FrontRebuildStatus(FrontRebuildPhase.FAILED, startedAt, clock.instant(), null, null,
                        "内部错误：任务异常终止"));
            }
            running.set(false);
        }
    }

    private void fail(Instant startedAt, String reason) {
        log.warn("全体重算中止：{}", reason);
        status.set(new FrontRebuildStatus(FrontRebuildPhase.FAILED, startedAt, clock.instant(), null, null, reason));
        nodeNotifyService.notifyFrontRebuildAborted(reason);
    }
}
