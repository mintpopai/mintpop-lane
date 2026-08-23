-- 席位账号：本次分配给用户的是哪个账号。与 credential_cipher（席位凭据密文）凑成「账号 + 凭据」一对，
-- 账号本身是明文、管理端可见可改，凭据只进不出。归属企业的订阅额外要求域名与企业 domain 一致，
-- 这条约束落在服务层（企业域名可改，数据库级约束会把历史行钉死），不建外键、不建唯一索引。
ALTER TABLE subscription
    ADD COLUMN account_email VARCHAR(128) NULL
        COMMENT '本次分配给用户的账号邮箱，如 zhang@acme.com；一律小写存储；归属企业时域名须与企业 domain 一致；NULL 表示未录'
        AFTER ends_at;
