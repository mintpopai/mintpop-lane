package ai.mintpop.lane.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 凭证有效期 = 订阅剩余时长 + 一天缓冲，上限 365 天、下限 1 天。
 *
 * 为什么跟随订阅：用户能从会话环境变量取到凭证明文，若签满一年，
 * 买一个月的人也能用满一年。有效期是压缩这个窗口的唯一可靠手段 ——
 * 它是「属性」，不像吊销那样依赖某个动作被正确触发。
 *
 * 为什么加一天缓冲：时钟或时区偏差会让用户在订阅最后一天突然不可用，
 * 那是可感知的故障；多给一天的损失可以忽略。
 */
@Component
public class CredentialLifetimeCalculator {

    /** 服务端上限，实测值 */
    private static final long MAX_SECONDS = 31536000L;
    /** 下限：过短的值可能被服务端拒绝，也没有实用价值 */
    private static final long MIN_SECONDS = Duration.ofDays(1).toSeconds();
    private static final Duration BUFFER = Duration.ofDays(1);

    private final Clock clock;

    public CredentialLifetimeCalculator(Clock clock) {
        this.clock = clock;
    }

    public long secondsFor(Instant endsAt) {
        long raw = Duration.between(clock.instant(), endsAt).plus(BUFFER).toSeconds();
        return Math.clamp(raw, MIN_SECONDS, MAX_SECONDS);
    }
}
