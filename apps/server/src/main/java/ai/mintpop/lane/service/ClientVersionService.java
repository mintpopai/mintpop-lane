package ai.mintpop.lane.service;

import ai.mintpop.lane.client.LatestClientVersionClient;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.util.ClientVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 桌面端强制更新的裁决：每发一个正式版，所有落后的客户端都必须升级才能继续用。
 * <p>
 * 客户端每个控制面请求都带 X-Client-Version，心跳 60 秒一次，所以新版发布后最迟
 * 「清单缓存 + 一次心跳」（约 2 分钟）所有在线客户端都会被拦下、进入强制更新。
 * <p>
 * 几条刻意的取舍：
 * - 不带版本头、或版本头形状不对，一律按旧版处理：那只可能是还没有这套机制的客户端。
 * - 清单拉取失败沿用上一次的值；从未拿到过（刚启动就拉不到）时只拦不带版本头的请求——
 *   不知道最新版是多少，就不能判任何一个带了版本号的客户端过期。
 * - 只拦「低于」最新版：等于或高于都放行，本地开发时客户端先于清单升了版本号也不会被误拦。
 */
@Slf4j
@Service
public class ClientVersionService {

    /**
     * 拉取状态快照，供管理端展示。整体替换而不是逐字段改，读的一方不会看到撕裂的组合。
     *
     * @param latest            当前认定的最新版；从未拉到过为 null
     * @param fetchedAt         最近一次拉取成功的时刻；从未成功为 null
     * @param lastAttemptAt     最近一次尝试拉取的时刻；还没尝试过为 null
     * @param lastAttemptFailed 最近一次尝试是否失败（失败时 latest 沿用上一次的值）
     */
    public record Status(ClientVersion latest, Instant fetchedAt, Instant lastAttemptAt, boolean lastAttemptFailed) {
    }

    private final LatestClientVersionClient latestClient;
    private final Clock clock;
    private final AtomicReference<Status> status = new AtomicReference<>(new Status(null, null, null, false));

    public ClientVersionService(LatestClientVersionClient latestClient, Clock clock) {
        this.latestClient = latestClient;
        this.clock = clock;
    }

    /**
     * 启动即拉一次（initialDelay=0），之后按 refreshInterval 重拉；管理端「立即拉取」也走这里。
     * 加锁只为让定时与手动两路不交错写出一份前后不一的状态，拉取本身有 5 秒超时，不会久占。
     *
     * @return 这一次是否拉取成功
     */
    @Scheduled(fixedDelayString = "#{@clientVersionProperties.refreshInterval.toMillis()}", initialDelay = 0)
    public synchronized boolean refresh() {
        Instant now = clock.instant();
        Optional<ClientVersion> fetched = latestClient.fetchLatest();
        Status previous = status.get();
        if (fetched.isEmpty()) {
            status.set(new Status(previous.latest(), previous.fetchedAt(), now, true));
            return false;
        }
        ClientVersion version = fetched.get();
        if (!version.equals(previous.latest())) {
            log.info("桌面端最新版本：{} -> {}", previous.latest(), version);
        }
        status.set(new Status(version, now, now, false));
        return true;
    }

    public Status status() {
        return status.get();
    }

    /** 当前已知的最新版本；从未拉到过为空 */
    public Optional<ClientVersion> latest() {
        return Optional.ofNullable(status.get().latest());
    }

    /** 客户端落后于最新版（含不带版本头）时抛 CLIENT_VERSION_OUTDATED */
    public void requireCurrent(String rawVersion) {
        Optional<ClientVersion> client = ClientVersion.parse(rawVersion);
        if (client.isEmpty()) {
            throw new BizException(BizCodeEnum.CLIENT_VERSION_OUTDATED);
        }
        ClientVersion known = status.get().latest();
        if (known != null && client.get().isOlderThan(known)) {
            throw new BizException(BizCodeEnum.CLIENT_VERSION_OUTDATED);
        }
    }
}
