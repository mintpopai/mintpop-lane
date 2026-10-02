package ai.mintpop.lane.service;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.FrontRebuildPhase;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.service.FrontRebuildRunner.RebuildResult;
import ai.mintpop.lane.service.SubRefreshService.RefreshOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("全体重算编排：互斥、先拉订阅、失败中止、状态与通知")
class FrontRebuildServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-29T03:00:00Z");

    @Mock private SubRefreshService subRefreshService;
    @Mock private FrontRebuildRunner runner;
    @Mock private NodeNotifyService nodeNotifyService;
    private FrontRebuildServiceImpl service;

    @BeforeEach
    void setUp() {
        // 同步执行器：测试里 start() 返回时任务已跑完，便于断言
        Executor inline = Runnable::run;
        service = new FrontRebuildServiceImpl(subRefreshService, runner, nodeNotifyService, inline, Clock.fixed(NOW, ZoneOffset.UTC));
        when(subRefreshService.refreshAllNow()).thenReturn(new RefreshOutcome(List.of()));
        when(runner.applyAll(anyBoolean())).thenReturn(new RebuildResult(120, 9, 0));
    }

    @Test
    @DisplayName("成功：先刷新订阅再重排，状态 SUCCEEDED 带人数，推完成通知")
    void succeeds() {
        service.start(false);

        var status = service.status();
        assertThat(status.phase()).isEqualTo(FrontRebuildPhase.SUCCEEDED);
        assertThat(status.userCount()).isEqualTo(120);
        assertThat(status.subscriptionCount()).isEqualTo(9);
        assertThat(status.startedAt()).isEqualTo(NOW);
        assertThat(status.finishedAt()).isEqualTo(NOW);
        verify(nodeNotifyService).notifyFrontRebuildFinished(120, 9, 0);
        verify(runner).applyAll(false);
    }

    @Test
    @DisplayName("任一订阅拉取失败：不碰分配，状态 FAILED 带订阅名，推中止通知")
    void fetchFailureAbortsRebuildBeforeTouchingLists() {
        when(subRefreshService.refreshAllNow()).thenReturn(new RefreshOutcome(List.of("泰山-01", "B-02")));

        service.start(false);

        verify(runner, never()).applyAll(anyBoolean());
        assertThat(service.status().phase()).isEqualTo(FrontRebuildPhase.FAILED);
        assertThat(service.status().error()).contains("泰山-01、B-02").doesNotContain("以下订阅");
        verify(nodeNotifyService).notifyFrontRebuildAborted(anyString());
    }

    @Test
    @DisplayName("容量不足：状态 FAILED 带服务端明细文案")
    void capacityFailureRecorded() {
        when(runner.applyAll(anyBoolean())).thenThrow(new BizException(BizCodeEnum.FRONT_CAPACITY_INSUFFICIENT, "需要 3 个主用名额，现有 2"));

        service.start(false);

        assertThat(service.status().phase()).isEqualTo(FrontRebuildPhase.FAILED);
        assertThat(service.status().error()).contains("需要 3 个主用名额，现有 2");
        verify(nodeNotifyService, never()).notifyFrontRebuildFinished(anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("正在跑时再 start 报 410055，不排队")
    void rejectsConcurrentStart() {
        // 用「挂起」的执行器：任务提交了但不跑，模拟 RUNNING 中
        service = new FrontRebuildServiceImpl(subRefreshService, runner, nodeNotifyService, task -> { }, Clock.fixed(NOW, ZoneOffset.UTC));
        service.start(false);
        assertThat(service.status().phase()).isEqualTo(FrontRebuildPhase.RUNNING);

        assertThatThrownBy(() -> service.start(false))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode()).isEqualTo(BizCodeEnum.FRONT_REBUILD_RUNNING);
    }

    @Test
    @DisplayName("初始状态 IDLE")
    void initiallyIdle() {
        assertThat(service.status().phase()).isEqualTo(FrontRebuildPhase.IDLE);
    }

    @Test
    @DisplayName("执行器拒绝提交：start 抛出、状态 FAILED、互斥已释放，之后换可用执行器能再启动")
    void executorRejectionReleasesLock() {
        Executor rejecting = task -> { throw new java.util.concurrent.RejectedExecutionException("满了"); };
        service = new FrontRebuildServiceImpl(subRefreshService, runner, nodeNotifyService, rejecting, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.start(false)).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        assertThat(service.status().phase()).isEqualTo(FrontRebuildPhase.FAILED);
        assertThat(service.status().error()).contains("RejectedExecutionException");

        // 互斥必须已释放：同一实例上再 start 不能报 410055（仍是拒绝执行器，抛的应是 Rejected 而非 BizException）
        assertThatThrownBy(() -> service.start(false)).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);

        FrontRebuildServiceImpl working = new FrontRebuildServiceImpl(subRefreshService, runner, nodeNotifyService,
                Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC));
        working.start(false);
        assertThat(working.status().phase()).isEqualTo(FrontRebuildPhase.SUCCEEDED);
    }

    @Test
    @DisplayName("保留手动分配：keepManual 透传给 runner，状态与通知带保留人数")
    void keepManualPassedThrough() {
        when(runner.applyAll(true)).thenReturn(new RebuildResult(100, 9, 20));

        service.start(true);

        assertThat(service.status().keptManualCount()).isEqualTo(20);
        verify(nodeNotifyService).notifyFrontRebuildFinished(100, 9, 20);
    }
}
