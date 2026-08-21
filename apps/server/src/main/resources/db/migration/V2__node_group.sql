-- 节点分组：一个订阅链接导入的节点归为一组，组上存链接以便重新拉取
CREATE TABLE node_group
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name           VARCHAR(64)  NOT NULL COMMENT '分组名，管理员起名',
    sub_url_cipher TEXT         NOT NULL COMMENT '订阅链接的 AES-GCM 密文（链接含 token 属凭据），形态为 Base64(IV||密文||认证标签)',
    remark         VARCHAR(255) NULL COMMENT '备注',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_group_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '节点分组：订阅链接导入的节点归组管理';

-- proxy_node 挂上分组与订阅来源信息；MIHOMO 通用协议加入取值说明
ALTER TABLE proxy_node
    MODIFY COLUMN protocol VARCHAR(16) NOT NULL COMMENT '协议：TROJAN / SOCKS5 / VMESS 决定哪些键算敏感；MIHOMO 为订阅导入的通用透传协议，整份参数加密存 secret_cipher',
    ADD COLUMN group_id    BIGINT       NULL COMMENT '所属分组 id，引用 node_group；NULL 表示手工节点、不属于任何分组' AFTER remark,
    ADD COLUMN source_name VARCHAR(128) NULL COMMENT '订阅里的原始节点名，重新拉取时按「同组内 source_name 相同」匹配同一节点；手工节点为 NULL' AFTER group_id,
    ADD COLUMN source_type VARCHAR(32)  NULL COMMENT '订阅节点的真实 mihomo type（如 anytls、vless），仅供列表展示，下发不读它；手工节点为 NULL' AFTER source_name,
    ADD CONSTRAINT fk_proxy_node_group FOREIGN KEY (group_id) REFERENCES node_group (id);
