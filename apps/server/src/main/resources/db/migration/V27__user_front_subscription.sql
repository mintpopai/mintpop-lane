CREATE TABLE user_front_subscription
(
    id                      BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id                 BIGINT   NOT NULL COMMENT '用户 id，引用 app_user',
    position                TINYINT  NOT NULL COMMENT '顺位：0 为主用（占订阅的主用容量），1、2 为备用（不占容量）',
    airport_subscription_id BIGINT   NOT NULL COMMENT '机场订阅 id，引用 airport_subscription；被引用时订阅不能删除',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_front_subscription_position (user_id, position),
    KEY idx_user_front_subscription_sub (airport_subscription_id),
    CONSTRAINT fk_user_front_subscription_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_front_subscription_sub FOREIGN KEY (airport_subscription_id) REFERENCES airport_subscription (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '用户的第一跳机场订阅列表：按顺位排列，每家机场最多一次，客户端外层 fallback 按此顺序兜底';
