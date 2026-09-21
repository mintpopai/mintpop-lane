package ai.mintpop.lane.client;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 给 {@link IpAsnClient} 套一层按 IP 的进程内 TTL 缓存。做成装饰器而不是改
 * {@link RestClientIpAsnClient}：缓存与「怎么查 ipwho.is」是两件事，分开各自可以独立测——
 * 缓存的测试不必起 HTTP 桩，HTTP 解析的测试也不必操心 TTL。装在接口这一层，
 * 对全部调用方（链路上报、入口 IP 巡检、采购尽调）一并生效。
 * <p>
 * 为什么必须有它：三期把 ASN 反查放进了心跳的同步路径，外部调用量级是
 * 用户数 × 故障域数 × 288/天（100 用户约 5.8 万次/天），且同一个来源 IP 每 5 分钟被重复问一次。
 * ipwho.is 是免密钥的免费服务，一旦限流，反查全线失败、运营商维度整体退化成「未知」。
 * IP → ASN 的归属一天之内基本不变，缓存因此几乎不损失精度。
 * <p>
 * 三条刻意的取舍：
 * <ul>
 *   <li><b>只缓存成功结果。</b>失败（限流、网络抖动、上游 5xx）不进缓存，否则一次抖动会把
 *       这个 IP 的「查不到」钉死一整个 TTL，运营商维度白白丢一天。</li>
 *   <li><b>取「现在」走注入的 {@link Clock}</b>（项目硬规则），TTL 因此可测，不必让测试真的睡 24 小时。</li>
 *   <li><b>容量上限 + 逐出最旧</b>，避免被大量不同来源 IP 撑成无界内存。逐出是尽力而为的：
 *       并发写入时容量可能短暂越过上限一点点，对一个几千条的缓存无所谓，不值得为它加锁。</li>
 * </ul>
 */
@Slf4j
public class CachingIpAsnClient implements IpAsnClient {

    /** IP 的 ASN 归属一天之内基本不变；再久就该重查了（机房搬迁、IP 段转让） */
    private static final Duration TTL = Duration.ofHours(24);

    /** 容量上限。按「活跃用户数量级」取，几千条的 record 内存占用可忽略 */
    private static final int DEFAULT_MAX_ENTRIES = 5_000;

    /** @param expiresAt 到这一刻（含）之前算有效；它同时也是入缓存顺序的代理量（TTL 固定） */
    private record CacheEntry(AsnInfo info, Instant expiresAt) {
    }

    private final IpAsnClient delegate;
    private final Clock clock;
    private final int maxEntries;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CachingIpAsnClient(IpAsnClient delegate, Clock clock) {
        this(delegate, clock, DEFAULT_MAX_ENTRIES);
    }

    /** 容量可注入的构造器，供测试用小容量验证逐出，不必真塞满几千条 */
    CachingIpAsnClient(IpAsnClient delegate, Clock clock, int maxEntries) {
        this.delegate = delegate;
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    @Override
    public Optional<AsnInfo> lookup(String ip) {
        Instant now = clock.instant();
        CacheEntry cached = cache.get(ip);
        if (cached != null && !cached.expiresAt().isBefore(now)) {
            return Optional.of(cached.info());
        }
        Optional<AsnInfo> fresh = delegate.lookup(ip);
        fresh.ifPresent(info -> store(ip, info, now));
        return fresh;
    }

    private void store(String ip, AsnInfo info, Instant now) {
        cache.put(ip, new CacheEntry(info, now.plus(TTL)));
        // 逐出到上限之内；evictOldest 返回 false（已空/被别的线程抢先删掉）时收手，不死循环
        while (cache.size() > maxEntries && evictOldest()) {
            // 空循环体：条件里已经完成逐出
        }
    }

    /** @return 是否真的逐出了一条 */
    private boolean evictOldest() {
        return cache.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .map(oldest -> cache.remove(oldest.getKey(), oldest.getValue()))
                .orElse(false);
    }
}
