-- 机场加「主用机场」标记：只有主用机场的订阅能被分配为用户的主用（第 0 位），
-- 非主用机场只当备用。存量机场默认为主用，上线后分配行为不变
ALTER TABLE airport
    ADD COLUMN primary_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否主用机场：1 其订阅可被分配为用户的主用（第 0 位），0 只当备用；取消后已分配的主用不自动迁走，下次分配/全体重算时才挪' AFTER remark;
