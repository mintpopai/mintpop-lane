package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.mapper.UserDeviceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 用户设备的 MySQL 实现。 */
@Repository
public class MybatisUserDeviceRepository implements UserDeviceRepository {

    private final UserDeviceMapper mapper;

    public MybatisUserDeviceRepository(UserDeviceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserDevice upsert(Long userId, String deviceId, String name, String os, String model, Instant now) {
        Optional<UserDevice> existing = findByUserAndDevice(userId, deviceId);
        if (existing.isPresent()) {
            UserDevice row = existing.get();
            mapper.update(null, Wrappers.<UserDevice>lambdaUpdate()
                    .eq(UserDevice::getId, row.getId())
                    .set(UserDevice::getName, name)
                    .set(UserDevice::getOs, os)
                    .set(UserDevice::getModel, model)
                    .set(UserDevice::getLastSeenAt, now));
            row.setName(name);
            row.setOs(os);
            row.setModel(model);
            row.setLastSeenAt(now);
            return row;
        }
        UserDevice fresh = new UserDevice();
        fresh.setUserId(userId);
        fresh.setDeviceId(deviceId);
        fresh.setName(name);
        fresh.setOs(os);
        fresh.setModel(model);
        fresh.setFirstSeenAt(now);
        fresh.setLastSeenAt(now);
        try {
            mapper.insert(fresh);
            return fresh;
        } catch (DuplicateKeyException e) {
            // 同一台设备并发上报：唯一键挡住了后一次插入，重查即可。
            // 这里不能吞掉「查不到」——那意味着唯一键之外还有别的问题，不该假装成功
            return findByUserAndDevice(userId, deviceId).orElseThrow(() -> e);
        }
    }

    @Override
    public boolean touchLastSeen(Long userId, String deviceId, Instant now, Instant staleBefore) {
        // 一条带条件的 UPDATE 同时完成三件事：定位本用户的那一行、判节流、写值。
        // 不先查后写——那既多一次往返，两次之间还会出现「都读到旧值、都判定该写」的重复写
        return mapper.update(null, Wrappers.<UserDevice>lambdaUpdate()
                .eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId)
                .lt(UserDevice::getLastSeenAt, staleBefore)
                .set(UserDevice::getLastSeenAt, now)) > 0;
    }

    @Override
    public List<UserDevice> findByUserId(Long userId) {
        return mapper.selectList(Wrappers.<UserDevice>lambdaQuery()
                .eq(UserDevice::getUserId, userId));
    }

    @Override
    public Optional<UserDevice> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(mapper.selectById(id));
    }

    private Optional<UserDevice> findByUserAndDevice(Long userId, String deviceId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<UserDevice>lambdaQuery()
                .eq(UserDevice::getUserId, userId)
                .eq(UserDevice::getDeviceId, deviceId)));
    }
}
