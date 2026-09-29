-- 二期：全局配置键值表 + 订阅拉取失败状态列
-- system_setting 表里没有的键按代码 SettingKey 的默认值走，迁移不预填任何行
CREATE TABLE system_setting
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    setting_key   VARCHAR(64)  NOT NULL COMMENT '配置键，取值为 SettingKey 枚举名（SCREAMING_SNAKE_CASE）',
    setting_value VARCHAR(255) NOT NULL COMMENT '配置值，字符串形态，类型由代码按键解释（枚举名或整数）',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_setting_key (setting_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '全局配置：键值表，当前承载第一跳的地区、每人机场数、每人带宽三项';

-- 订阅拉取失败状态：刷新失败不动节点、只记状态，管理端据此显示「拉取失败，自 xx 起」
ALTER TABLE airport_subscription
    ADD COLUMN fetch_failed_since DATETIME NULL COMMENT '订阅拉取连续失败的起始时间（UTC）；NULL 表示最近一次拉取成功' AFTER fetched_at,
    ADD COLUMN last_fetch_error VARCHAR(255) NULL COMMENT '最近一次拉取失败的错误说明；拉取成功后清空' AFTER fetch_failed_since;

-- 每人带宽改为全局配置项，不再写死 20
ALTER TABLE airport_subscription
    MODIFY COLUMN bandwidth_mbps INT NOT NULL COMMENT '订阅总带宽（Mbps），全部连接共享；主用容量 = bandwidth_mbps / 全局配置的每人带宽 向下取整；创建后不可改';
