package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.UserDevice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 用户已知设备的读写口。 */
public interface UserDeviceRepository {

    /**
     * 登记一台设备：不存在则建，已存在则刷新展示信息与最近上报时刻。
     * 设备信息以**最近一次上报**为准——用户改了主机名、升了系统，管理员该看到新的那份。
     * 返回登记后的那一行（带 id）。
     */
    UserDevice upsert(Long userId, String deviceId, String name, String os, String model, Instant now);

    /**
     * 刷新一台已登记设备的最近上报时刻，用于「这台机器还在不在用」。
     *
     * <p>节流内建在 SQL 条件里：只有 last_seen_at 早于 staleBefore 才真的写。调用方按请求来，
     * 一台活跃设备每分钟就有一次心跳，无条件写会把这张表变成高频写入点；把窗口做成条件而不是
     * 调用方的内存状态，多实例部署与重启都不会让节流失效。
     *
     * <p>机器码未登记时什么也不做：设备行由 upsert 在绑定/申请时建，那里才有主机名与系统版本；
     * 凭一个请求头就建行会把从没绑过任何订阅的机器也记进来，而它们没有活跃时刻可言。
     *
     * @return 是否真的写了。false 表示被节流挡下或该设备未登记，两者都不是错误
     */
    boolean touchLastSeen(Long userId, String deviceId, Instant now, Instant staleBefore);

    /** 某用户的全部已知设备。一个人的设备数是个位数，一次取回避免下发链路配置时逐条查 */
    List<UserDevice> findByUserId(Long userId);

    Optional<UserDevice> findById(Long id);
}
