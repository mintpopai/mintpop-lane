-- 落地出口时区：管理端录入出口 IP 时由 GeoIP 识别预填、可人工修正，随表单存库。
-- 参与后续按落地时区的业务（调度/下发），仅落地节点有值。
ALTER TABLE proxy_node
    ADD COLUMN egress_timezone VARCHAR(64) NULL COMMENT '落地出口 IP 对应的 IANA 时区名（如 Asia/Tokyo），仅 LAND 节点有值；NULL 表示未填' AFTER egress_ip;
