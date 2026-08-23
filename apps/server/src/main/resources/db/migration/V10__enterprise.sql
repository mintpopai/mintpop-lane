-- 企业：管理端维护的客户单位。企业本身只是一份档案（名称 + 域名 + 支持的 agent 类型），
-- 真正的业务作用是给订阅一个「归属」维度：分配订阅时可选归属某企业，留空即个人订阅。

CREATE TABLE enterprise
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '企业名称，管理员起名，全局唯一',
    domain      VARCHAR(128) NOT NULL COMMENT '企业域名，如 acme.com；一律小写存储，全局唯一',
    agent_types JSON         NOT NULL COMMENT '本企业支持的 agent 类型列表，如 ["CLAUDE","CODEX"]；分配订阅时按它过滤可选套餐',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用状态：1 启用，0 停用；停用后不能再分配新订阅，存量订阅不受影响',
    remark      VARCHAR(255) NULL COMMENT '备注，管理员自用说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_enterprise_name (name),
    UNIQUE KEY uk_enterprise_domain (domain)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '企业：客户单位档案，订阅可归属到它';

-- 订阅的企业归属。与 plan_id 同为弱引用、不设外键：企业删除由服务层把关
-- （被订阅引用时拒绝删除），不靠数据库级联，避免历史订阅被静默改写
ALTER TABLE subscription
    ADD COLUMN enterprise_id BIGINT NULL COMMENT '归属企业 id；NULL 表示个人订阅' AFTER user_id;
