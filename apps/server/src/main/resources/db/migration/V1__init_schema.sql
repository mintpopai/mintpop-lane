-- 代理节点池：FRONT（第一跳出国）与 LAND（第二跳落地）两类节点同表，用 role 区分
CREATE TABLE proxy_node
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(64)  NOT NULL COMMENT '运维可读的节点名，如 US-机场-01',
    role          VARCHAR(16)  NOT NULL COMMENT '节点角色：FRONT 第一跳出国，LAND 第二跳落地',
    protocol      VARCHAR(16)  NOT NULL COMMENT '协议：TROJAN / SOCKS5 / VMESS，决定哪些键算敏感',
    server_addr   VARCHAR(255) NOT NULL COMMENT '节点地址，明文存储以便列表展示与检索',
    port          INT          NOT NULL COMMENT '节点端口',
    extra_config  JSON         NULL COMMENT '非敏感的 mihomo 透传键，如 sni、network、skip-cert-verify',
    secret_cipher TEXT         NULL COMMENT '敏感键 JSON 的 AES-GCM 密文，形态为 Base64(IV||密文||认证标签)',
    egress_ips    JSON         NULL COMMENT '出口 IP 集合，仅 LAND 节点有值，供客户端做出口校验',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 可分配可下发，DISABLED 禁用',
    remark        VARCHAR(255) NULL COMMENT '备注',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_proxy_node_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '代理节点池：链式代理的两跳节点';

-- 终端用户：一人一落地出口、一人一 Claude 席位
CREATE TABLE app_user
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    subject                  VARCHAR(128) NOT NULL COMMENT 'Logto 中的 user id，即 JWT 的 sub',
    name                     VARCHAR(64)  NOT NULL COMMENT '姓名，用于页面展示与日志排查',
    role                     VARCHAR(16)  NOT NULL DEFAULT 'MEMBER' COMMENT '角色：ADMIN 可用管理端，MEMBER 仅可用终端',
    status                   VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 正常，SUSPENDED 临时停用，REVOKED 已吊销',
    front_node_id            BIGINT       NOT NULL COMMENT '第一跳节点 id，引用 proxy_node',
    land_node_id             BIGINT       NULL COMMENT '第二跳落地节点 id，引用 proxy_node；NULL 表示尚未分配',
    claude_credential_cipher TEXT         NULL COMMENT 'Claude 席位长效凭据的 AES-GCM 密文',
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_subject (subject),
    -- 一个落地节点最多绑一个人：唯一索引不约束多个 NULL，故「未分配」可以有很多条，
    -- 而「同一落地被两人共用」在数据库层面直接插不进去，不依赖应用层自觉
    UNIQUE KEY uk_app_user_land_node (land_node_id),
    CONSTRAINT fk_app_user_front_node FOREIGN KEY (front_node_id) REFERENCES proxy_node (id),
    CONSTRAINT fk_app_user_land_node FOREIGN KEY (land_node_id) REFERENCES proxy_node (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '终端用户与其专属链路、席位的绑定';
