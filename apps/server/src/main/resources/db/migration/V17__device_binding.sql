-- 一份订阅只能在一台设备上使用。
--
-- 强制点在服务端：拉链路配置时按客户端上报的机器码判定，未绑定或绑在别处的订阅
-- 一律不下发席位凭据。客户端那三种绑定状态只用于把原因如实讲给用户听。
--
-- 机器码是客户端对硬件标识做的 SHA-256（macOS 的 IOPlatformUUID / Windows 的 MachineGuid），
-- 原始标识不出用户本机，服务端只存哈希。已知边界：它是客户端自报的，人为伪造可以骗过服务端——
-- 本方案挡的是常规共享（买一份装给朋友），不是 DRM 级防护。
--
-- 换机不做自助：用户在新设备提申请，服务端推飞书通知管理员，管理员在管理端同意后改绑。
-- 自助改绑（哪怕带冷却期）等于允许「轮流用」，那正是要挡的场景。

-- 用户的已知设备。同一用户同一机器码只存一行，展示信息按最近一次上报更新
CREATE TABLE user_device
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL COMMENT '归属用户 id，引用 app_user；删用户级联删设备',
    device_id     CHAR(64)     NOT NULL COMMENT '机器码：客户端对硬件标识做的 SHA-256，小写十六进制定长 64',
    name          VARCHAR(128) NOT NULL COMMENT '主机名，管理员据此辨认是哪台机器',
    os            VARCHAR(64)  NOT NULL COMMENT '系统与版本，如 macos 26.6.1 / windows 11',
    model         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '机型，如 Mac17,9；客户端取不到时为空串，不参与判定',
    first_seen_at DATETIME     NOT NULL COMMENT '首次上报时刻（UTC）',
    last_seen_at  DATETIME     NOT NULL COMMENT '最近一次上报时刻（UTC）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_device (user_id, device_id),
    -- 级联删：设备记录只对它归属的那个用户有意义，人没了，这些行既无人可查也无处可用
    CONSTRAINT fk_user_device_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '用户的已知设备：机器码与供管理员辨认的设备信息';

-- 订阅上的绑定关系。两列同空同有值：bound_device_id 为 NULL 即「还没在任何设备上用过」
ALTER TABLE subscription
    ADD COLUMN bound_device_id BIGINT  NULL COMMENT '绑定的设备 id，弱引用 user_device(id)、不设外键（解绑与删设备都允许悬空）；NULL 表示未绑定',
    ADD COLUMN bound_at        DATETIME NULL COMMENT '绑定时刻（UTC）；与 bound_device_id 同空同有值';

-- 换机申请。一份订阅同时只允许有一条 PENDING——MySQL 无部分唯一索引，
-- 由服务层保证（提新申请前把旧的 PENDING 置 SUPERSEDED），故这里只建普通索引；
-- 并发下靠 DeviceBindingServiceImpl.requestRebind 在订阅行上取 FOR UPDATE 行锁串行化，
-- 否则两次同时提交会双双作废 0 条、双双插入，留下两条 PENDING
CREATE TABLE device_rebind_request
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_no      VARCHAR(32) NOT NULL COMMENT '申请号：DR + yyyyMMddHHmmss + 6 位随机数字；飞书卡片与工单里引用它',
    subscription_id BIGINT      NOT NULL COMMENT '申请改绑的订阅，弱引用 subscription(id)',
    user_id         BIGINT      NOT NULL COMMENT '申请人，引用 app_user；删用户级联删申请',
    from_device_id  BIGINT      NULL COMMENT '申请时绑定的设备 id，弱引用 user_device(id)；此前未绑定则为 NULL',
    to_device_id    BIGINT      NOT NULL COMMENT '申请改绑到的设备 id，弱引用 user_device(id)，即提交申请那台机器',
    reason          VARCHAR(255) NULL COMMENT '用户填的理由，可空——不强制填写，强制只会逼出没有信息量的字符',
    status          VARCHAR(16) NOT NULL COMMENT '状态：PENDING 待处理 / APPROVED 已同意 / REJECTED 已拒绝 / SUPERSEDED 已被同订阅的新申请或解绑作废',
    decided_by      BIGINT      NULL COMMENT '处理的管理员 user id；未处理为 NULL',
    decided_at      DATETIME    NULL COMMENT '处理时刻（UTC）；未处理为 NULL',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（UTC）',
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_rebind_request_no (request_no),
    KEY idx_device_rebind_status (status, created_at),
    KEY idx_device_rebind_subscription (subscription_id, status),
    -- 与 fk_user_device_user 同样级联删：申请是「某人想把自己的订阅挪到自己另一台机器上」，
    -- 用户一删就再无意义；不级联的话删用户会被这里的外键挡住而失败（管理端删用户直接报错）
    CONSTRAINT fk_device_rebind_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '换机申请：用户在新设备上请求把订阅改绑过来，由管理员裁决';
