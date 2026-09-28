-- 第一跳改为按机场订阅分配，不做旧数据迁移（spec §3.1）：先清空第一跳相关数据，
-- 再给订阅加上 NOT NULL 的所属机场、账号、带宽
DELETE FROM user_front_node;
UPDATE app_user SET front_node_id = NULL;
DELETE FROM proxy_node WHERE role = 'FRONT';
DELETE FROM airport_subscription;

CREATE TABLE airport
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '机场名，管理员起名，全局唯一',
    website_url VARCHAR(255) NULL COMMENT '机场地址（官网或用户中心），NULL 表示未填',
    remark      VARCHAR(255) NULL COMMENT '备注',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_airport_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '机场：机场订阅的供应方；每个用户的第一跳列表里每家机场最多出现一次';

ALTER TABLE airport_subscription
    ADD COLUMN airport_id     BIGINT       NOT NULL COMMENT '所属机场 id，引用 airport；创建后不可改' AFTER id,
    ADD COLUMN account        VARCHAR(128) NOT NULL COMMENT '购买该订阅所用的机场账号（如邮箱），自由文本' AFTER name,
    ADD COLUMN bandwidth_mbps INT          NOT NULL COMMENT '订阅总带宽（Mbps），全部连接共享；主用容量 = bandwidth_mbps / 20 向下取整；创建后不可改' AFTER account,
    ADD CONSTRAINT fk_airport_subscription_airport FOREIGN KEY (airport_id) REFERENCES airport (id);
