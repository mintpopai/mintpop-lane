-- 第一跳不再按节点分配，也不再有手工前置节点（spec §3.1）
DROP TABLE user_front_node;

ALTER TABLE app_user
    DROP FOREIGN KEY fk_app_user_front_node,
    DROP COLUMN front_node_id;

-- 第一跳只能来自机场订阅：不属于任何订阅的前置节点无处下发
DELETE FROM proxy_node WHERE role = 'FRONT' AND airport_subscription_id IS NULL;
