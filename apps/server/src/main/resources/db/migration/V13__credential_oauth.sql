-- 席位凭证从「管理员本机 claude setup-token」改为「服务端 OAuth 签发」。
-- 凭证本身仍存 credential_cipher（形态未变），这里补的是签发元数据：
-- 有了 scope 才知道该不该给客户端注入 CLAUDE_CODE_OAUTH_SCOPES，
-- 有了 expires_at 才谈得上「凭证有效期不超过订阅时长」这条约束。
-- 五列全部可空：credential_scope 为空即旧式凭证，客户端行为退回改动前。

ALTER TABLE subscription
    ADD COLUMN credential_scope VARCHAR(200) NULL COMMENT '服务端实际授予的 scope，空格分隔；为空表示旧式凭证（仅 inference）',
    ADD COLUMN credential_token_uuid VARCHAR(64) NULL COMMENT 'Anthropic 侧的 token 唯一标识，供审计与排障',
    ADD COLUMN credential_issued_at TIMESTAMP NULL COMMENT '凭证签发时刻',
    ADD COLUMN credential_expires_at TIMESTAMP NULL COMMENT '凭证到期时刻；应与订阅止期保持同步',
    ADD COLUMN credential_refresh_cipher TEXT NULL COMMENT 'refresh_token 密文；第一版不使用，仅作吊销后备与自动续期预留（与 credential_cipher 同用 TEXT，密文长度取决于 token 长度，VARCHAR 定长会顶到上限报错）';

-- 授权会话：管理员在浏览器授权与把 code 贴回后台是两次请求，
-- PKCE 的 code_verifier 必须跨请求留存。不放内存是因为服务端可能多副本，
-- 且这张表天然是一份签发审计记录。
CREATE TABLE oauth_session
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id           VARCHAR(64)  NOT NULL COMMENT '会话标识，返回给前端用于兑换',
    subscription_id      BIGINT       NOT NULL COMMENT '本次签发的目标订阅',
    code_verifier_cipher VARCHAR(500) NOT NULL COMMENT 'PKCE code_verifier 密文；与 code 组合即可换出凭证，必须加密',
    state                VARCHAR(100) NOT NULL COMMENT 'OAuth state，回调校验用',
    scope                VARCHAR(200) NOT NULL COMMENT '本次请求的 scope',
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时刻',
    expires_at           TIMESTAMP    NOT NULL COMMENT '会话到期时刻，逾期不可兑换',
    UNIQUE KEY uk_oauth_session_sid (session_id),
    KEY idx_oauth_session_subscription (subscription_id)
) COMMENT '席位凭证签发的授权会话，生命周期 30 分钟';
