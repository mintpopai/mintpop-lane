package ai.mintpop.lane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 全链路 UTC：业务取「现在」一律注入本 Clock 后 clock.instant()，
 * 不直接调 Instant.now()——测试可用 Clock.fixed 固定时间。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
