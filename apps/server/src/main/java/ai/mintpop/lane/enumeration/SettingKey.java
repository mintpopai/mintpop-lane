package ai.mintpop.lane.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局配置的键。表 system_setting 里没有该键的行时，按 defaultValue 走，迁移不预填。
 * 值一律以字符串存，由 SystemSettingServiceImpl 按键解释成枚举或整数。
 */
@Getter
@AllArgsConstructor
public enum SettingKey {
    /** 第一跳节点筛选地区，NodeRegion 枚举名 */
    FRONT_REGION("US"),
    /** 每个用户分配几家机场的订阅（1 主用 + n-1 备用） */
    FRONT_AIRPORTS_PER_USER("3"),
    /** 每个用户按多少带宽（Mbps）计主用名额 */
    FRONT_BANDWIDTH_PER_USER_MBPS("20");

    private final String defaultValue;
}
