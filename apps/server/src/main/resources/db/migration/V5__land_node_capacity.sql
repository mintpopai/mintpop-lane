-- 落地节点从「一人一座」改为容量制：节点上新增容量上限（默认 10，管理端可改），
-- 分配校验按「当前绑定人数 < capacity」判定；已用量实时统计、不落库，取消分配自然回补。
ALTER TABLE proxy_node
    ADD COLUMN capacity INT NOT NULL DEFAULT 10 COMMENT '落地节点容量：最多可绑定的用户数，仅 LAND 节点有意义；默认 10' AFTER egress_timezone;

-- 一人一座靠的唯一索引随之删除；land_node_id 上补一个普通索引，
-- 供按节点反查/计数与外键 fk_app_user_land_node 使用（先加再删，删时列上始终有索引）。
-- 不做数据回填：存量绑定天然满足「每节点 ≤ 10 人」（此前一节点最多一人）。
ALTER TABLE app_user
    ADD INDEX idx_app_user_land_node (land_node_id),
    DROP INDEX uk_app_user_land_node;
