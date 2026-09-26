package ai.mintpop.lane.client;

import ai.mintpop.lane.client.IpAsnClient.AsnInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IP → ASN 反查的进程内缓存。
 * <p>
 * 三期把这个反查从「低频尽调/巡检」提到了心跳的同步路径上：每个带上报块的心跳都要查一次，
 * 外部调用量 = 用户数 × 故障域数 × 288/天（100 用户约 5.8 万次/天），而同一个来源 IP 每 5 分钟
 * 被重复问一遍。免费的 ipwho.is 一旦限流，反查全线失败、运营商维度整体退化成「未知」。
 * IP → ASN 一天之内基本不变，缓存是这里最划算的一招。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ASN 反查缓存")
class CachingIpAsnClientTest {

    private static final String IP = "203.0.113.9";
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    private static final AsnInfo INFO = new AsnInfo("AS4134", "China Telecom");

    @Mock
    private IpAsnClient delegate;

    @Mock
    private Clock clock;

    private CachingIpAsnClient client;

    @BeforeEach
    void setUp() {
        client = new CachingIpAsnClient(delegate, clock);
    }

    @Test
    @DisplayName("同一个 IP 的第二次反查直接命中缓存，不再打下游")
    void secondLookupHitsCacheWithoutCallingDelegate() {
        when(clock.instant()).thenReturn(NOW);
        when(delegate.lookup(IP)).thenReturn(Optional.of(INFO));

        assertThat(client.lookup(IP)).contains(INFO);
        assertThat(client.lookup(IP)).contains(INFO);

        verify(delegate, times(1)).lookup(IP);
    }

    @Test
    @DisplayName("TTL（24 小时）过期后重新反查——IP 归属会变，缓存不能永久有效")
    void expiredEntryIsLookedUpAgain() {
        when(clock.instant()).thenReturn(NOW);
        when(delegate.lookup(IP)).thenReturn(Optional.of(INFO));
        client.lookup(IP);

        when(clock.instant()).thenReturn(NOW.plusSeconds(24 * 3600 + 1));
        client.lookup(IP);

        verify(delegate, times(2)).lookup(IP);
    }

    @Test
    @DisplayName("恰好卡在 TTL 边界上仍算命中，不多打一次")
    void entryIsStillValidExactlyAtTtlBoundary() {
        when(clock.instant()).thenReturn(NOW);
        when(delegate.lookup(IP)).thenReturn(Optional.of(INFO));
        client.lookup(IP);

        when(clock.instant()).thenReturn(NOW.plusSeconds(24 * 3600 - 1));
        client.lookup(IP);

        verify(delegate, times(1)).lookup(IP);
    }

    @Test
    @DisplayName("反查失败不进缓存：只缓存成功结果，否则一次限流/抖动会把这个 IP 钉死 24 小时")
    void failureIsNotCached() {
        when(clock.instant()).thenReturn(NOW);
        when(delegate.lookup(IP)).thenReturn(Optional.empty());

        assertThat(client.lookup(IP)).isEmpty();
        assertThat(client.lookup(IP)).isEmpty();

        verify(delegate, times(2)).lookup(IP);
    }

    @Test
    @DisplayName("不同 IP 各自独立缓存，互不串味")
    void differentIpsAreCachedIndependently() {
        when(clock.instant()).thenReturn(NOW);
        when(delegate.lookup(IP)).thenReturn(Optional.of(INFO));
        when(delegate.lookup("198.51.100.7")).thenReturn(Optional.of(new AsnInfo("AS16509", "Amazon.com")));

        assertThat(client.lookup(IP)).contains(INFO);
        assertThat(client.lookup("198.51.100.7")).contains(new AsnInfo("AS16509", "Amazon.com"));
        assertThat(client.lookup(IP)).contains(INFO);

        verify(delegate, times(1)).lookup(IP);
        verify(delegate, times(1)).lookup("198.51.100.7");
    }

    @Test
    @DisplayName("只关心 ASN 的老调用方（lookupAsn）同样吃到缓存——装饰器包在接口上，对全部调用方生效")
    void lookupAsnAlsoHitsCache() {
        when(clock.instant()).thenReturn(NOW);
        when(delegate.lookup(IP)).thenReturn(Optional.of(INFO));

        assertThat(client.lookupAsn(IP)).contains("AS4134");
        assertThat(client.lookupAsn(IP)).contains("AS4134");

        verify(delegate, times(1)).lookup(IP);
        // 默认方法委托 lookup，装饰器不必也不该再重写一遍 lookupAsn
        verify(delegate, never()).lookupAsn(IP);
    }

    @Test
    @DisplayName("TTL 可配：1 分钟 TTL 下第 61 秒再查会重新打下游")
    void ttlIsConfigurable() {
        CachingIpAsnClient shortLived = new CachingIpAsnClient(delegate, clock, Duration.ofMinutes(1), 10);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(61));
        when(delegate.lookup(IP)).thenReturn(Optional.of(INFO));

        shortLived.lookup(IP);
        shortLived.lookup(IP);

        verify(delegate, times(2)).lookup(IP);
    }

    @Test
    @DisplayName("超过容量上限时逐出最旧的条目，最近用过的留在缓存里——内存不能无界增长")
    void oldestEntryIsEvictedWhenOverCapacity() {
        CachingIpAsnClient small = new CachingIpAsnClient(delegate, clock, 2);
        when(delegate.lookup("ip-1")).thenReturn(Optional.of(new AsnInfo("AS1", null)));
        when(delegate.lookup("ip-2")).thenReturn(Optional.of(new AsnInfo("AS2", null)));
        when(delegate.lookup("ip-3")).thenReturn(Optional.of(new AsnInfo("AS3", null)));

        // 三个条目分别在三个时刻入缓存，「最旧」因此有确定的含义
        when(clock.instant()).thenReturn(NOW);
        small.lookup("ip-1");
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        small.lookup("ip-2");
        when(clock.instant()).thenReturn(NOW.plusSeconds(2));
        small.lookup("ip-3"); // 装第三个时超出容量，最旧的 ip-1 被逐出

        small.lookup("ip-3"); // 最近装进来的仍在缓存里
        verify(delegate, times(1)).lookup("ip-3");

        small.lookup("ip-1"); // 已被逐出，要重新反查
        verify(delegate, times(2)).lookup("ip-1");
    }
}
