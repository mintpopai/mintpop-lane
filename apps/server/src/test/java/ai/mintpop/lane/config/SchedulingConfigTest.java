package ai.mintpop.lane.config;

import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@code spring.task.scheduling.pool.size} 配置真的生效：5 个 {@code @Scheduled}
 * 任务共享同一个调度器，池大小为 1（Spring Boot 默认）时会互相排队，跑得慢的任务
 * （如订阅刷新拉取多个分组）会顶掉其它任务的执行时机。
 */
class SchedulingConfigTest extends MysqlTestBase {

    @Autowired
    private ThreadPoolTaskScheduler scheduler;

    @Test
    @DisplayName("定时任务调度器线程池为 2，五个定时任务不再互相排队")
    void schedulerPoolSizeIsTwo() {
        assertThat(scheduler.getPoolSize()).isEqualTo(2);
    }
}
