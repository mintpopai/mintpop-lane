-- 第一跳列表加「手动分配」标记：管理员手动指定的列表，全体重算选「保留手动分配」时原样保留。
-- 同一用户的各行取值相同；存量列表都是自动分配出来的，默认 0。
-- 列名不用 manual：MySQL 8.4 早期小版本的解析器把 MANUAL 当关键字，裸写会报 1064
ALTER TABLE user_front_subscription
    ADD COLUMN manually_assigned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否管理员手动指定：1 手动（全体重算可选择保留），0 自动分配；同一用户各行取值相同' AFTER airport_subscription_id;
