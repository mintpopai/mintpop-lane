-- 用户自助购买（控制台）：新增套餐订单表；订阅起止时间放开为可空——
-- 付款入账时建出的订阅起期永远由管理员在管理端填，填之前两列同为 NULL，即「待开通」。

ALTER TABLE subscription
    MODIFY COLUMN starts_at DATETIME NULL COMMENT '服务起期（含，UTC）；NULL 表示待开通（自助购买建出、管理员尚未填起期），与 ends_at 同空同有值',
    MODIFY COLUMN ends_at   DATETIME NULL COMMENT '服务止期（不含，UTC）；NULL 表示待开通。在期判定为 starts_at <= now < ends_at，纯查询、无定时任务';

CREATE TABLE plan_order
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no           VARCHAR(32)   NOT NULL COMMENT '订单号：LN + yyyyMMddHHmmss + 6 位随机数字；给用户看，也写入 Stripe PaymentIntent 的 metadata.orderId',
    user_id            BIGINT        NOT NULL COMMENT '买家，app_user(id)',
    plan_id            BIGINT        NOT NULL COMMENT '所购套餐 id，弱引用 plan(id)、不设外键（套餐硬删允许悬空）；历史呈现以快照列为准',
    name               VARCHAR(64)   NOT NULL COMMENT '套餐名快照',
    agent_type         VARCHAR(16)   NOT NULL COMMENT 'agent 类型快照：CLAUDE / CODEX',
    plan_duration_days INT           NOT NULL COMMENT '套餐时长快照（天）',
    plan_price         DECIMAL(10,2) NOT NULL COMMENT '套餐价格快照，展示用',
    plan_currency      VARCHAR(8)    NOT NULL COMMENT '币种快照：USD / CNY',
    amount_minor       BIGINT        NOT NULL COMMENT '应付金额，最小货币单位整数（USD / CNY 均为 price×100），下单时定死；入账校验与 Stripe 请求都用它',
    status             VARCHAR(16)   NOT NULL COMMENT '状态：PENDING 待支付 / PAID 已支付（履约已完成，终态）/ CANCELLED 用户取消 / EXPIRED 超时未付 / FAILED 支付失败（可续付）',
    payment_provider   VARCHAR(16)   NULL COMMENT '支付处理方，固定 stripe；未发起支付为空',
    payment_trade_no   VARCHAR(64)   NULL COMMENT 'Stripe PaymentIntent id；发起支付即落，client_secret 不入库',
    paid_at            DATETIME      NULL COMMENT '入账时刻（UTC）',
    subscription_id    BIGINT        NULL COMMENT '履约建出的订阅 id，弱引用 subscription(id)；PAID 后必有',
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_order_order_no (order_no),
    KEY idx_plan_order_user (user_id, created_at),
    CONSTRAINT fk_plan_order_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '套餐订单：用户在控制台自助购买套餐的订单与支付记录';
