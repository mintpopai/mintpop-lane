ALTER TABLE proxy_node
    ADD COLUMN failure_domain VARCHAR(255) NULL COMMENT '故障域：该节点域名解析链的终点（CNAME 末端；无 CNAME 则为节点域名本身）。同一故障域下的节点共用一台中转入口机，不构成冗余。NULL 表示尚未解析或解析失败' AFTER source_type,
    ADD COLUMN failure_domain_checked_at DATETIME NULL COMMENT '故障域最近一次解析成功的时间（UTC）；NULL 表示从未解析成功' AFTER failure_domain,
    ADD KEY idx_proxy_node_failure_domain (failure_domain);

ALTER TABLE node_group
    ADD COLUMN traffic_used_bytes  BIGINT   NULL COMMENT '订阅已用流量字节数（upload+download），取自 subscription-userinfo 响应头；NULL 表示机场未返回该头' AFTER remark,
    ADD COLUMN traffic_total_bytes BIGINT   NULL COMMENT '订阅总流量额度字节数；NULL 同上' AFTER traffic_used_bytes,
    ADD COLUMN traffic_expires_at  DATETIME NULL COMMENT '订阅到期时间（UTC）；NULL 同上' AFTER traffic_total_bytes,
    ADD COLUMN traffic_alerted_pct TINYINT  NULL COMMENT '已推送过额度告警的档位（80 或 95）；用量回落或跨更高档才再推，避免每轮刷屏。NULL 表示未推过' AFTER traffic_expires_at,
    ADD COLUMN fetched_at          DATETIME NULL COMMENT '最近一次成功拉取订阅的时间（UTC）' AFTER traffic_alerted_pct;

CREATE TABLE entry_ip_history
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    failure_domain VARCHAR(255) NOT NULL COMMENT '故障域（中转入口域名）',
    vantage        VARCHAR(16)  NOT NULL COMMENT '解析视角：CHINA_TELECOM / CHINA_UNICOM / CHINA_MOBILE / OVERSEAS',
    entry_ips      VARCHAR(255) NOT NULL COMMENT '该视角解析到的入口 IP，多个以逗号分隔并按字典序排列',
    asns           VARCHAR(255) NULL COMMENT '入口 IP 对应的 ASN，顺序与 entry_ips 一致；反查失败为 NULL',
    observed_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '观测时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_entry_ip_history_domain_vantage (failure_domain, vantage, observed_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '中转入口 IP 变更历史：IP 一变说明机场刚换机，是封锁事件的间接信号';
