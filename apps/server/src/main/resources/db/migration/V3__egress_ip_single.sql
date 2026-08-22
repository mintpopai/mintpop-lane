-- 出口 IP 从 JSON 集合收敛为单条：产品语义是「一人一固定落地出口」，
-- 出口校验也随之从集合成员收紧为逐字相等；多条场景（出口池/轮换）与产品定位冲突，不支持。
-- 旧值不回填，直接清空：现存数据量小，出口 IP 由管理员在管理端重新填写。
ALTER TABLE proxy_node
    ADD COLUMN egress_ip VARCHAR(64) NULL COMMENT '出口 IP，仅 LAND 节点有值，供客户端做出口校验；NULL 表示未填' AFTER secret_cipher;

ALTER TABLE proxy_node
    DROP COLUMN egress_ips;
