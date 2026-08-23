-- 去掉「用户名」这一概念：用户的唯一业务标识收口到邮箱。
-- name 原本只是 Logto id_token 的展示用姓名（缺失还要用邮箱 @ 前缀兜底），从不参与身份判定，
-- 留着只会让「这个人是谁」有两种称呼；email 则从「仅展示、不作唯一键」升级为唯一约束，
-- 管理端列表、搜索、运维 SQL 一律按它定位人。身份查档键仍是 subject，本次不动。

-- ⚠️ 上线前置检查（手工执行）：下面这条有输出说明库里已有重复邮箱，
-- 必须先人工合并/清理这些账号，否则本迁移的唯一索引会加失败、服务起不来：
--   SELECT email, COUNT(*) FROM app_user GROUP BY email HAVING COUNT(*) > 1;

ALTER TABLE app_user
    DROP COLUMN name;

ALTER TABLE app_user
    MODIFY COLUMN email VARCHAR(255) NOT NULL COMMENT '邮箱，登录时从 id_token 同步；用户的唯一业务标识';

ALTER TABLE app_user
    ADD UNIQUE KEY uk_app_user_email (email);
