package ai.mintpop.lane.client;

import ai.mintpop.lane.exception.BizException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

/**
 * 给 {@link SubFetchClient} 套一层重试。做成装饰器而不是改 RestClientSubFetchClient：
 * 「怎么拉」与「失败了怎么重试」分开各自可测。装在接口这一层，导入、5 分钟刷新、全体重算
 * 三条拉取路径一并生效。
 * <p>
 * 规则：最多 3 次；两次之间退避 2 秒、5 秒；机场返回 429 且带 Retry-After 时按它等（上限 60 秒，
 * 别被一个离谱的头钉死线程）。任何 BizException 都重试——拉取层只会抛 SUB_FETCH_FAILED。
 */
@Slf4j
public class RetryingSubFetchClient implements SubFetchClient {

    static final int MAX_ATTEMPTS = 3;
    static final List<Duration> BACKOFFS = List.of(Duration.ofSeconds(2), Duration.ofSeconds(5));
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

    private final SubFetchClient delegate;
    private final Sleeper sleeper;

    public RetryingSubFetchClient(SubFetchClient delegate, Sleeper sleeper) {
        this.delegate = delegate;
        this.sleeper = sleeper;
    }

    @Override
    public SubFetchResult fetch(String subUrl) {
        BizException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return delegate.fetch(subUrl);
            } catch (BizException e) {
                last = e;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                Duration wait = waitBefore(attempt, e);
                log.warn("订阅拉取第 {} 次失败，{} 后重试", attempt, wait);
                sleeper.sleep(wait);
            }
        }
        throw last;
    }

    private static Duration waitBefore(int failedAttempt, BizException e) {
        if (e instanceof SubFetchRateLimitedException limited && limited.getRetryAfter() != null) {
            Duration hinted = limited.getRetryAfter();
            return hinted.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : hinted;
        }
        return BACKOFFS.get(failedAttempt - 1);
    }
}
