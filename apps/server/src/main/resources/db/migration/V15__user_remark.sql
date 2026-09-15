-- 用户备注：管理员在管理端给用户挂的自用说明（如「老客户」「试用期」），
-- 与 proxy_node / subscription / plan / enterprise 上的 remark 同一形态。
-- 用户可见面（桌面端、官网）拿不到这一列，它只出现在 /api/admin/** 的响应里。
ALTER TABLE app_user
    ADD COLUMN remark VARCHAR(255) NULL COMMENT '备注，管理员自用说明' AFTER land_node_id;
