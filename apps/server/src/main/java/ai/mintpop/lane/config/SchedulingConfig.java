package ai.mintpop.lane.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 @Scheduled 定时任务，共 5 个：
 * EgressCheckService 的出口 IP 巡检（默认 10 分钟一轮）、
 * SubRefreshService 的订阅定时刷新（默认 24 小时一轮）、
 * LinkReportArchiveService 的上报归档（默认 1 小时一轮）、
 * LinkReportAlertService 的告警扫描（默认 5 分钟一轮）、
 * ClientVersionService 的桌面端最新版本拉取（默认 1 分钟一轮）。
 * <p>
 * Spring Boot 默认调度器只有 1 个线程，这些任务共享它会互相排队——跑得慢的一个
 * （如订阅刷新要拉取多个订阅）会顶掉其它任务本该执行的时机。池大小（2）在
 * {@code application.yaml} 的 {@code spring.task.scheduling.pool.size} 配置，不在这里写死。
 * <p>
 * 单独成类而不放在 NotifyConfig：调度是通用基础设施，不属于通知模块，通知整体拿掉也不该连定时任务一起失效。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
