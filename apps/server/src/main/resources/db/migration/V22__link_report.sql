CREATE TABLE link_report
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT       NOT NULL COMMENT '上报用户 id，引用 app_user',
    failure_domain    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '故障域＝节点域名 CNAME 链的终点。空串表示尚未解析出故障域（不是"没有故障域"）。刻意用空串而非 NULL：可空列在 UNIQUE 索引里不参与唯一性判断，重复上报会去不了重',
    window_start      DATETIME     NOT NULL COMMENT '上报窗口的起点（UTC）。窗口长度 5 分钟，由客户端按心跳周期对齐',
    samples           INT          NOT NULL COMMENT '窗口内的有效采样次数（mihomo history 非空的次数），成功率的分母',
    alive_count       INT          NOT NULL COMMENT '有效采样里 alive 为真的次数，成功率的分子',
    no_sample_count   INT          NOT NULL DEFAULT 0 COMMENT 'history 为空、无法判定通断的次数。刻意单列并且不计入 samples：mihomo 的 alive 在无历史时默认 true，混进分子会把"从没测过"记成"健康"',
    p50_latency_ms    INT          NULL COMMENT 'alive 为真的那些采样的延迟中位数（毫秒）；窗口内没有 alive 样本时为 NULL。注意 0 是合法的低延迟、不是失败',
    failovers         INT          NOT NULL DEFAULT 0 COMMENT '窗口内该故障域对应的 fallback 组 now 字段发生变化的次数，即故障转移次数',
    resolved_entry_ip VARCHAR(45)  NULL COMMENT '客户端实际解析到的中转入口 IP（IPv6 最长 45 字符）；客户端解析失败时为 NULL。服务端算不出这个值，机场按运营商分线路解析',
    source_asn        VARCHAR(32)  NULL COMMENT '按上报请求的来源 IP 反查到的 ASN；反查失败为 NULL。刻意由服务端反查而不让客户端自报——自报不可信，客户端也不知道',
    isp               VARCHAR(64)  NULL COMMENT 'ASN 对应的运营商名；反查失败为 NULL',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_link_report_window (user_id, failure_domain, window_start),
    KEY idx_link_report_window_start (window_start),
    CONSTRAINT fk_link_report_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '链路上报的窗口聚合：客户端按 5 分钟窗口报一次第一跳健康状况。保留 7 天，之后由归档任务压成按天聚合';

CREATE TABLE link_report_daily
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT       NOT NULL COMMENT '用户 id，引用 app_user',
    failure_domain  VARCHAR(255) NOT NULL DEFAULT '' COMMENT '故障域；空串表示尚未解析（与 link_report 同一编码）',
    isp             VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '运营商名；空串表示 ASN 反查失败。注意与 link_report.isp 编码不同——那一列是可空的、NULL 表示反查失败；本列进了唯一键，而 MySQL 的 UNIQUE 对 NULL 不做唯一性判断，可空列进唯一键会让去重失效，故必须 NOT NULL DEFAULT。归档时要把 link_report.isp 的 NULL 转成空串',
    stat_date       DATE         NOT NULL COMMENT '统计日（UTC 日历日）',
    samples         BIGINT       NOT NULL COMMENT '当日有效采样总数',
    alive_count     BIGINT       NOT NULL COMMENT '当日 alive 总数',
    no_sample_count BIGINT       NOT NULL DEFAULT 0 COMMENT '当日无法判定的次数',
    failovers       BIGINT       NOT NULL DEFAULT 0 COMMENT '当日故障转移总次数',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_link_report_daily (user_id, failure_domain, isp, stat_date),
    KEY idx_link_report_daily_date (stat_date),
    CONSTRAINT fk_link_report_daily_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '链路上报的按天聚合：由归档任务从 link_report 压出，保留 90 天。刻意不带 p50 延迟——中位数不可跨窗口相加，硬算会得到一个没有意义的数';
