package ai.mintpop.lane.repository;

import ai.mintpop.lane.enumeration.SettingKey;

import java.util.Map;
import java.util.Optional;

/** 全局配置键值表的读写口。值是字符串，类型解释在 SystemSettingService */
public interface SystemSettingRepository {

    Optional<String> findValue(SettingKey key);

    /** 表里有的键；没有的键不出现，调用方按 SettingKey.getDefaultValue() 兜底 */
    Map<SettingKey, String> findAll();

    /** 有则更新、无则插入 */
    void save(SettingKey key, String value);
}
