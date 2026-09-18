CREATE TABLE user_front_node
(
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT   NOT NULL COMMENT '用户 id，引用 app_user',
    node_id    BIGINT   NOT NULL COMMENT '前置节点 id，引用 proxy_node',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_front_node (user_id, node_id),
    CONSTRAINT fk_user_front_node_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_front_node_node FOREIGN KEY (node_id) REFERENCES proxy_node (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '用户可用的前置节点集合：按故障域分散下发，供客户端组 fallback 组';

-- 把现有的单选前置节点回填进关联表，保证二期上线时每个已分配用户至少有一个前置节点
INSERT INTO user_front_node (user_id, node_id)
SELECT id, front_node_id FROM app_user WHERE front_node_id IS NOT NULL;

-- app_user.front_node_id 保留不删，语义收窄为「主前置节点」＝fallback 组首选，
-- 同时是老客户端唯一能理解的那一个字段；完整集合见 user_front_node
ALTER TABLE app_user
    MODIFY COLUMN front_node_id BIGINT NULL COMMENT '主前置节点 id，引用 proxy_node：fallback 组的首选，也是老客户端唯一能理解的那一个；完整集合见 user_front_node。NULL 表示尚未分配';
