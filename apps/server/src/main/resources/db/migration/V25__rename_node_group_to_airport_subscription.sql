-- 「节点分组」改名为「机场订阅」：一个分组本来就是一份机场订阅（spec §3.3）。
-- 只改名不动数据，列语义不变；机场、账号、带宽三列在 V26 加
ALTER TABLE proxy_node DROP FOREIGN KEY fk_proxy_node_group;

RENAME TABLE node_group TO airport_subscription;

ALTER TABLE airport_subscription
    RENAME INDEX uk_node_group_name TO uk_airport_subscription_name,
    MODIFY COLUMN name VARCHAR(64) NOT NULL COMMENT '订阅名，管理员起名，全局唯一',
    COMMENT = '机场订阅：一份从机场买来的订阅链接，其下节点由订阅导入，是第一跳分配与限速的单位';

-- RENAME COLUMN 与引用新列名的 MODIFY COLUMN 拆成两条语句：MySQL 8.x 在同一条 ALTER TABLE 里
-- 校验各子句时仍按原表结构走，同语句内引用刚改名的列会报 Unknown column，必须拆开执行
ALTER TABLE proxy_node
    RENAME COLUMN group_id TO airport_subscription_id;

ALTER TABLE proxy_node
    MODIFY COLUMN airport_subscription_id BIGINT NULL COMMENT '所属机场订阅 id，引用 airport_subscription；NULL 表示手工节点（只有落地节点会是 NULL）',
    MODIFY COLUMN source_name VARCHAR(128) NULL COMMENT '订阅里的原始节点名，重新拉取时按「同订阅内 source_name 相同」匹配同一节点；手工节点为 NULL',
    ADD CONSTRAINT fk_proxy_node_airport_subscription FOREIGN KEY (airport_subscription_id) REFERENCES airport_subscription (id);
