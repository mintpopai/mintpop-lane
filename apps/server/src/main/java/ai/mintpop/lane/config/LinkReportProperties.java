package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 链路上报（三期可观测性）的非敏感参数。默认值未经生产实测，全部可在
 * {@code config/application.yml} 按需覆盖，见 {@code application.example.yml} 里的注释。
 */
@Data
@Component
@ConfigurationProperties(prefix = "link-report")
public class LinkReportProperties {

    /** 成功率跌破这个值就告警。⚠️ 未经生产实测，先按经验取值，有数据后再调 */
    private double alertThreshold = 0.80;

    /**
     * 最小样本量：低于它一律不告警。样本太少时比值没有意义（用户刚上线、或整段窗口离线），
     * 按 0/0 当成 0% 报出去会在最不该吵的时候吵
     */
    private int alertMinSamples = 20;

    /** 原始窗口保留天数，超过由归档任务压成按天聚合 */
    private int rawRetentionDays = 7;

    /** 按天聚合保留天数 */
    private int dailyRetentionDays = 90;

    /**
     * 上报窗口起点允许比「现在」落后多久，超过就丢弃整条上报块。
     * 客户端时钟不准、或请求被构造出任意时间时，过旧的窗口会污染按天聚合的口径——
     * 归档任务按「当天」聚合，一条几天前的窗口混进「今天」的聚合行没有任何校验能拦住
     */
    private Duration windowMaxPast = Duration.ofHours(1);

    /**
     * 上报窗口起点允许比「现在」超前多久，超过就丢弃整条上报块。
     * 未来时间戳的窗口会让 {@code findWindowsBefore} 永远扫不到它（比较条件是
     * 「窗口起点早于给定时刻」），归档任务因此永远清不掉这一行
     */
    private Duration windowMaxFuture = Duration.ofMinutes(5);
}
