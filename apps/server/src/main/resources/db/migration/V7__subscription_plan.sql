-- 分配收口到套餐：订阅只能从现有套餐选出。落库时快照套餐关键信息（套餐可改名改价甚至硬删，
-- 历史记录不能随之失真），并为每次分配生成对外引用的唯一分配号。

-- 项目未上线，手填时代的订阅数据无保留价值，直接清空：
-- 空表上加 NOT NULL 列 + 唯一键一步到位，不需要任何存量回填
DELETE FROM subscription;

ALTER TABLE subscription
    ADD COLUMN assignment_no CHAR(32) NOT NULL COMMENT '分配号：本次分配的唯一业务标识（32 位十六进制 UUID），对外引用一律用它、不用自增 id' AFTER id,
    ADD COLUMN plan_id BIGINT NOT NULL COMMENT '所选套餐 id，弱引用 plan(id)、不设外键（套餐硬删不受牵制，允许悬空）；历史呈现以快照列为准' AFTER agent_type,
    ADD COLUMN plan_duration_days INT NOT NULL COMMENT '套餐时长快照（天），分配时拷贝；止期 = 起期 + 本值，套餐后续改动不影响本记录' AFTER name,
    ADD COLUMN plan_price DECIMAL(10, 2) NOT NULL COMMENT '套餐价格快照，分配时拷贝' AFTER plan_duration_days,
    ADD COLUMN plan_currency VARCHAR(8) NOT NULL COMMENT '套餐币种快照：USD / CNY' AFTER plan_price,
    MODIFY COLUMN name VARCHAR(64) NOT NULL COMMENT '用户可见的套餐名，分配时从所选套餐快照，不再由管理员手填',
    ADD UNIQUE KEY uk_subscription_assignment_no (assignment_no);
