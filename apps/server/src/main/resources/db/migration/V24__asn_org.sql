CREATE TABLE asn_org
(
    asn           VARCHAR(32) NOT NULL COMMENT 'ASN，形如 AS4134，与 link_report.source_asn 同一写法',
    org_name      VARCHAR(64) NOT NULL COMMENT '首次反查到该 ASN 时 ipwho.is 给的组织名（connection.isp），只做展示、不做键；最长 64 字符，超长由服务端截断',
    first_seen_at DATETIME    NOT NULL COMMENT '首次见到该 ASN 的时间（UTC）',
    PRIMARY KEY (asn)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'ASN 到展示名的映射：运营商维度以 ASN 做键、名字只做展示，避免上游文案漂移把同一运营商裂成两列';
