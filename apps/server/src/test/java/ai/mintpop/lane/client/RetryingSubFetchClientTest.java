package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("订阅拉取重试装饰器")
class RetryingSubFetchClientTest {

    private static final SubFetchResult OK = new SubFetchResult("proxies: []", null, null, null, null);

    private final List<Duration> slept = new ArrayList<>();
    private final Sleeper recordingSleeper = slept::add;

    @Test
    @DisplayName("第一次成功不重试、不睡")
    void succeedsFirstTime() {
        AtomicInteger calls = new AtomicInteger();
        SubFetchClient client = new RetryingSubFetchClient(url -> { calls.incrementAndGet(); return OK; }, recordingSleeper);

        assertThat(client.fetch("https://a/sub")).isSameAs(OK);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(slept).isEmpty();
    }

    @Test
    @DisplayName("前两次失败第三次成功：共 3 次调用，退避 2 秒、5 秒")
    void retriesTwiceThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        SubFetchClient client = new RetryingSubFetchClient(url -> {
            if (calls.incrementAndGet() < 3) throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
            return OK;
        }, recordingSleeper);

        assertThat(client.fetch("https://a/sub")).isSameAs(OK);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(slept).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("三次都失败：抛最后一次的异常，只调 3 次")
    void givesUpAfterThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        SubFetchClient client = new RetryingSubFetchClient(url -> {
            calls.incrementAndGet();
            throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
        }, recordingSleeper);

        assertThatThrownBy(() -> client.fetch("https://a/sub"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode()).isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(slept).hasSize(2);
    }

    @Test
    @DisplayName("429 带 Retry-After 时按它等，而不是固定退避；超过 60 秒截到 60 秒")
    void honoursRetryAfter() {
        AtomicInteger calls = new AtomicInteger();
        SubFetchClient client = new RetryingSubFetchClient(url -> {
            int n = calls.incrementAndGet();
            if (n == 1) throw new SubFetchRateLimitedException(Duration.ofSeconds(7));
            if (n == 2) throw new SubFetchRateLimitedException(Duration.ofSeconds(600));
            return OK;
        }, recordingSleeper);

        client.fetch("https://a/sub");
        assertThat(slept).containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("429 不带 Retry-After 时走固定退避")
    void rateLimitedWithoutHeaderUsesBackoff() {
        AtomicInteger calls = new AtomicInteger();
        SubFetchClient client = new RetryingSubFetchClient(url -> {
            if (calls.incrementAndGet() == 1) throw new SubFetchRateLimitedException(null);
            return OK;
        }, recordingSleeper);

        client.fetch("https://a/sub");
        assertThat(slept).containsExactly(Duration.ofSeconds(2));
    }
}
