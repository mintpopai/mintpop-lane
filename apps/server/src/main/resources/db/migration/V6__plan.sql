-- 套餐：管理端维护的可售卖选项，固定时长 + 定价；目前只作选项维护，不与用户/订阅关联
CREATE TABLE plan
(
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(64)    NOT NULL COMMENT '套餐名称，管理员起名，全局唯一',
    duration_days INT            NOT NULL COMMENT '套餐时长（天），正整数，如 30',
    price         DECIMAL(10, 2) NOT NULL COMMENT '价格，两位小数，非负',
    currency      VARCHAR(8)     NOT NULL DEFAULT 'USD' COMMENT '币种：USD / CNY',
    enabled       TINYINT(1)     NOT NULL DEFAULT 1 COMMENT '上架状态：1 上架，0 停用；停用不删除，历史可追溯',
    remark        VARCHAR(255)   NULL COMMENT '备注，管理员自用说明',
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '套餐：固定时长与定价的可售卖选项';
