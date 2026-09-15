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

    /** 某用户的全部已知设备。一个人的设备数是个位数，一次取回避免下发链路配置时逐条查 */
    List<UserDevice> findByUserId(Long userId);

    Optional<UserDevice> findById(Long id);
}
