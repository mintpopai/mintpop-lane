CREATE TABLE link_alert_state
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL COMMENT '用户 id，引用 app_user',
    failure_domain VARCHAR(255) NOT NULL DEFAULT '' COMMENT '故障域；空串表示尚未解析（与 link_report 同一编码）',
    isp            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '运营商名；空串表示 ASN 反查失败。故障域级告警与运营商级告警共用本表，故障域级的行 isp 存空串',
    alerted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '当前是否处于已告警状态：1 表示已推过且尚未恢复，用于抑制重复推送；恢复正常时置 0，下次再劣化会重新推',
    alerted_at     DATETIME     NULL COMMENT '最近一次推送时间（UTC）；从未推过为 NULL',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_link_alert_state (user_id, failure_domain, isp),
    CONSTRAINT fk_link_alert_state_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '链路告警的去重状态：记住每个「用户 × 故障域 × 运营商」当前推没推过，避免持续劣化时每轮刷屏';
