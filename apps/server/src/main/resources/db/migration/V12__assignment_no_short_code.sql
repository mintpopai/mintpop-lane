-- 分配号从 32 位十六进制 UUID 换成 10 位 Crockford Base32 短码。
-- 分配号的唯一职责是「给用户看」：要能一眼读完、能在工单里口述、能手抄，32 位十六进制三样都做不到。
-- 字母表去掉 I / L / O / U，杜绝与 1 / 0 混淆；入库不带连字符，分组（7K3M9-QX2FT）只是展示形态。
-- 程序内部引用一律仍走自增 id，分配号不承担引用职责。

-- 项目未上线，存量订阅都是手工造的测试数据，无保留价值：清空后直接改列宽，不做回填
DELETE FROM subscription;

ALTER TABLE subscription
    MODIFY COLUMN assignment_no CHAR(10) NOT NULL
        COMMENT '分配号：给用户看的分配标识，10 位 Crockford Base32 大写短码（如 7K3M9QX2FT），展示时分两组；程序内部引用走自增 id';
