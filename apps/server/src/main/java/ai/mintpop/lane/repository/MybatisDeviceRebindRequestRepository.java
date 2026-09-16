package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.mapper.DeviceRebindRequestMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 换机申请的 MySQL 实现。 */
@Repository
public class MybatisDeviceRebindRequestRepository implements DeviceRebindRequestRepository {

    private final DeviceRebindRequestMapper mapper;

    public MybatisDeviceRebindRequestRepository(DeviceRebindRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long create(DeviceRebindRequest request) {
        request.setId(null);
        mapper.insert(request);
        return request.getId();
    }

    @Override
    public Optional<DeviceRebindRequest> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<DeviceRebindRequest> findByStatus(RebindRequestStatus status) {
        return mapper.selectList(Wrappers.<DeviceRebindRequest>lambdaQuery()
                .eq(DeviceRebindRequest::getStatus, status)
                .orderByDesc(DeviceRebindRequest::getCreatedAt)
                .orderByDesc(DeviceRebindRequest::getId));
    }

    @Override
    public List<DeviceRebindRequest> findAll() {
        return mapper.selectList(Wrappers.<DeviceRebindRequest>lambdaQuery()
                .orderByDesc(DeviceRebindRequest::getCreatedAt)
                .orderByDesc(DeviceRebindRequest::getId));
    }

    @Override
    public List<DeviceRebindRequest> findPendingByUserId(Long userId) {
        return mapper.selectList(Wrappers.<DeviceRebindRequest>lambdaQuery()
                .eq(DeviceRebindRequest::getUserId, userId)
                .eq(DeviceRebindRequest::getStatus, RebindRequestStatus.PENDING));
    }

    @Override
    public int supersedePending(Long subscriptionId) {
        return mapper.update(null, Wrappers.<DeviceRebindRequest>lambdaUpdate()
                .eq(DeviceRebindRequest::getSubscriptionId, subscriptionId)
                .eq(DeviceRebindRequest::getStatus, RebindRequestStatus.PENDING)
                .set(DeviceRebindRequest::getStatus, RebindRequestStatus.SUPERSEDED));
    }

    @Override
    public boolean decide(Long id, RebindRequestStatus to, Long adminUserId, Instant now) {
        return mapper.update(null, Wrappers.<DeviceRebindRequest>lambdaUpdate()
                .eq(DeviceRebindRequest::getId, id)
                .eq(DeviceRebindRequest::getStatus, RebindRequestStatus.PENDING)
                .set(DeviceRebindRequest::getStatus, to)
                .set(DeviceRebindRequest::getDecidedBy, adminUserId)
                .set(DeviceRebindRequest::getDecidedAt, now)) > 0;
    }
}
