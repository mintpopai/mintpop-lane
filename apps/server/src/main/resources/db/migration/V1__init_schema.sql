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

-- 终端用户：身份（Logto sub + 邮箱）+ 链路绑定（一人一落地出口）+ 账号处置态。
-- 服务权益（买了什么、到什么时候、席位凭据）在 subscription 表，不在这里。
CREATE TABLE app_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    subject       VARCHAR(128) NOT NULL COMMENT 'Logto 中的 user id，即 id_token 的 sub，身份唯一键',
    email         VARCHAR(255) NOT NULL COMMENT '邮箱，登录时从 id_token 同步；仅展示与联系用，不作唯一键',
    name          VARCHAR(64)  NOT NULL COMMENT '姓名，登录时从 id_token 同步，缺失用邮箱 @ 前缀兜底',
    role          VARCHAR(16)  NOT NULL DEFAULT 'MEMBER' COMMENT '角色：ADMIN 可用管理端，MEMBER 仅可用终端',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账号处置态：ACTIVE 正常，SUSPENDED 临时停用，REVOKED 已吊销；「能不能用服务」由订阅决定，不由本列表达',
    front_node_id BIGINT       NULL COMMENT '第一跳节点 id，引用 proxy_node；NULL 表示尚未分配（注册即无资源）',
    land_node_id  BIGINT       NULL COMMENT '第二跳落地节点 id，引用 proxy_node；NULL 表示尚未分配',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（首次登录时间）',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_subject (subject),
    -- 一个落地节点最多绑一个人：唯一索引不约束多个 NULL，故「未分配」可以有很多条
    UNIQUE KEY uk_app_user_land_node (land_node_id),
    CONSTRAINT fk_app_user_front_node FOREIGN KEY (front_node_id) REFERENCES proxy_node (id),
    CONSTRAINT fk_app_user_land_node FOREIGN KEY (land_node_id) REFERENCES proxy_node (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '终端用户：身份、链路绑定与账号处置态';

-- 服务开通记录：一次购买 = 一条记录，按 agent 分开卖。
-- 同一用户同一 agent 可并存多条（多席位并行、续费衔接的新时段），故无 (user_id, agent_type) 唯一约束。
CREATE TABLE subscription
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT       NOT NULL COMMENT '归属用户 id，引用 app_user；删用户级联删订阅',
    agent_type        VARCHAR(16)  NOT NULL COMMENT 'agent 类型：CLAUDE / CODEX',
    name              VARCHAR(64)  NOT NULL COMMENT '用户可见的套餐名，管理员填写，如「Claude Max 席位 1」',
    starts_at         DATETIME     NOT NULL COMMENT '服务起期（含）',
    ends_at           DATETIME     NOT NULL COMMENT '服务止期（不含）；在期判定为 starts_at <= now < ends_at，纯查询、无定时任务',
    credential_cipher TEXT         NULL COMMENT '该 agent 的席位凭据密文（AES-GCM），NULL 表示尚未录入',
    remark            VARCHAR(255) NULL COMMENT '备注',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_subscription_user (user_id),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '服务开通记录：用户购买的按 agent 的服务时段与席位凭据';
