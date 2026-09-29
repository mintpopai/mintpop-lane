package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.SystemSetting;
import ai.mintpop.lane.enumeration.SettingKey;
import ai.mintpop.lane.mapper.SystemSettingMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class MybatisSystemSettingRepository implements SystemSettingRepository {

    private final SystemSettingMapper mapper;

    public MybatisSystemSettingRepository(SystemSettingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findValue(SettingKey key) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<SystemSetting>lambdaQuery()
                        .eq(SystemSetting::getSettingKey, key.name())))
                .map(SystemSetting::getSettingValue);
    }

    @Override
    public Map<SettingKey, String> findAll() {
        Map<SettingKey, String> result = new EnumMap<>(SettingKey.class);
        for (SystemSetting row : mapper.selectList(null)) {
            // 表里若残留代码已删掉的键，跳过而不是让整个读取炸掉
            try {
                result.put(SettingKey.valueOf(row.getSettingKey()), row.getSettingValue());
            } catch (IllegalArgumentException ignored) {
                // 未知键忽略
            }
        }
        return result;
    }

    @Override
    public void save(SettingKey key, String value) {
        SystemSetting existing = mapper.selectOne(Wrappers.<SystemSetting>lambdaQuery()
                .eq(SystemSetting::getSettingKey, key.name()));
        if (existing == null) {
            SystemSetting row = new SystemSetting();
            row.setSettingKey(key.name());
            row.setSettingValue(value);
            mapper.insert(row);
        } else {
            existing.setSettingValue(value);
            mapper.updateById(existing);
        }
    }
}
