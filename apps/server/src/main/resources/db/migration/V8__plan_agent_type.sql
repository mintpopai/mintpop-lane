-- 套餐绑定 agent 类型：建套餐时即指定它面向哪个 agent，分配订阅不再单独选类型，
-- 订阅上的 agent_type 变成分配时从套餐取的快照（与名称/价格快照同一语义）。

-- 存量套餐先按 CLAUDE 补齐（上线前若有 Codex 用途的套餐，需管理端手动改回），
-- 随后去掉默认值：新建套餐必须显式指定，不允许静默落成 CLAUDE
ALTER TABLE plan
    ADD COLUMN agent_type VARCHAR(16) NOT NULL DEFAULT 'CLAUDE' COMMENT 'agent 类型：CLAUDE / CODEX' AFTER name;

ALTER TABLE plan
    ALTER COLUMN agent_type DROP DEFAULT;
