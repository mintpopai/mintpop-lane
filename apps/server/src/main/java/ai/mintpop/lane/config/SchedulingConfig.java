package ai.mintpop.lane.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 @Scheduled 定时任务：EgressCheckService 的出口 IP 巡检（按 EgressCheckProperties.interval 跑）、
 * SubRefreshService 的订阅定时刷新（按 SubRefreshProperties.interval 跑）。
 * 单独成类而不放在 NotifyConfig：调度是通用基础设施，不属于通知模块，通知整体拿掉也不该连定时任务一起失效。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
