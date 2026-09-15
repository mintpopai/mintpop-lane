package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.enumeration.RebindRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 换机申请的读写口。状态迁移一律是「条件 UPDATE + 返回是否生效」：
 * 两个管理员同时裁决同一条申请的竞态靠 WHERE 里的来源状态挡，服务层不做查-判-写。
 */
public interface DeviceRebindRequestRepository {

    /** 新建，返回自增主键。申请号撞唯一键抛 DuplicateKeyException，由调用方重试 */
    Long create(DeviceRebindRequest request);

    Optional<DeviceRebindRequest> findById(Long id);

    /** 按状态查，按创建时间倒序 */
    List<DeviceRebindRequest> findByStatus(RebindRequestStatus status);

    /** 全部申请，按创建时间倒序 */
    List<DeviceRebindRequest> findAll();

    /** 某用户全部待处理的申请。下发链路配置时用它算每条席位的 pendingRequest */
    List<DeviceRebindRequest> findPendingByUserId(Long userId);

    /**
     * 把某订阅现存的 PENDING 申请全部置为 SUPERSEDED，返回影响行数。
     * 两个场景都要调：用户提了新申请（旧的没意义了）、管理员直接解绑（改绑的目标没意义了）。
     */
    int supersedePending(Long subscriptionId);

    /** 裁决：仅当仍为 PENDING 时置为 to 并记下处理人与时刻。返回 false 即已被处理过 */
    boolean decide(Long id, RebindRequestStatus to, Long adminUserId, Instant now);
}
