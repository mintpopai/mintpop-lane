-- 席位账号所属组织的身份，签发凭证时从 Anthropic 的 /api/oauth/profile 取得。
--
-- credential_org_uuid 的用途不是审计，而是让客户端能替用户跨过 Claude Code 的
-- Fable 5 计费同意弹窗：CLI 把「用户已同意」按组织 UUID 记在 ~/.claude.json 的
-- fableOverageConsentV2 里，键对不上等于没记。客户端本地拿不到这个值（首次会话时
-- 那份配置里还没有，残留值又可能属于用户自己的账号），故由服务端下发。
--
-- credential_extra_usage_enabled 记录签发时该组织有没有开启 usage credits。
-- 它是上面那条路能走通的前提：关闭时上游返回的 overage-disabled-reason 是
-- org_level_disabled，不在 CLI 的放行白名单内，同意记录再全也没用。管理端据此提示
-- 管理员去开，免得「凭证签发成功、用户却用不了 Fable」这种只能靠排查才发现的状态。
--
-- 两列均可空：为空即旧式凭证（本次改动之前签发的），客户端按空串跳过预置、退回弹窗。

ALTER TABLE subscription
    ADD COLUMN credential_org_uuid VARCHAR(64) NULL COMMENT '席位账号所属组织的 UUID，客户端据此预置 Fable 计费同意',
    ADD COLUMN credential_extra_usage_enabled BOOLEAN NULL COMMENT '签发时该组织是否已开启 usage credits；为 false 时 Fable 预置不生效，管理端应提示';
