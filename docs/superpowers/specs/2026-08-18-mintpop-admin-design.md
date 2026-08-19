# Mintpop 管控后台 · 设计文档

- 日期：2026-08-18
- 状态：已评审，待实现
- 子项目：第四期（管控后台）
- 前置：`2026-08-16-mintpop-terminal-design.md`（终端本体设计，第 13 节把管控后台列为后续子项目）

## 1. 背景与目标

第一期把员工绑定表与节点信息落在服务端的 `application-prod.yaml` 里，靠只读挂载进容器。这个形态在人数极少时够用，但有三个硬限制：

1. **加人 / 停用 / 换落地出口都要登部署机改文件 + 重启服务**，运维动作不可审计、易手抖。
2. **落地出口与用户的对应关系没有任何约束**，同一个落地代理被写给两个人（IP 撞车）不会被发现。
3. **节点信息散落**：`front` 是全局一份、`land` 内嵌在每条 employee 里，想换机场节点或做灰度都得逐条改。

本期把这些数据从配置文件下沉到数据库，并提供一个管理端 SPA，让上述运维动作在页面上完成、即时生效。

**成功标准**：现网从此不再需要为「加人 / 停用 / 换节点 / 换席位凭据」登部署机改 YAML；落地出口一人一个由数据库约束保证。

## 2. 范围

### 本期做

- 服务端存储切换到外置 MySQL，员工绑定表与节点池成为唯一真源
- 全量把 `Employee` 概念改名为 `User`
- 敏感字段（Claude 席位凭据、节点密码）应用层加密存储
- 管理端 API（`/api/admin/**`）：用户增删改查、节点池增删改查、落地出口分配
- 管理端前端 `apps/admin`（Vue 3 SPA）、镜像、CI/CD、部署接入

### 本期不做

- **使用审计与报表**（心跳、下发、出口校验失败的事件记录与统计）
- **管理端操作日志**（谁在什么时候改了什么）
- **凭据批量轮换**（页面上逐条改凭据本期就支持；批量轮换与到期提醒不做）
- 多租户、部门/分组、细粒度数据权限
- 从旧 YAML 自动导入数据（见第 11 节：手工录入）

## 3. 命名统一：Employee → User

项目尚未上线，没有兼容负担，一次改到底。

| 现在 | 改成 |
|---|---|
| `entity/Employee` | `entity/User`（表映射）+ `dto/UserDto`（明文领域对象） |
| `enumeration/EmployeeStatus` | `enumeration/UserStatus`（取值 `ACTIVE` / `SUSPENDED` / `REVOKED` 不变） |
| `repository/EmployeeRepository` | `repository/UserRepository` |
| `repository/PropertiesEmployeeRepository` | 删除（数据源切库） |
| Rust `link::remote::EmployeeStatus` | `link::remote::UserStatus` |
| 配置节 `mintpop.link.employees` / `mintpop.link.front` | 删除（下沉到数据库） |
| `BizCodeEnum` 中「员工」字样 | 改为「用户」 |
| 两份 spec / plan 文档中的「员工绑定表」等术语 | 统一为「用户」 |

**对外协议零变更**：`HeartbeatResponse` 仍是 `{"status": "ACTIVE"}`，`LinkConfigResponse` 字段不变。桌面端只改 Rust 类型名，现有 `cargo test` 用例即可守住反序列化行为。

## 4. 数据模型

外置 MySQL 8.0+（需要 JSON 列）。表结构由 **Flyway** 管理，`V1__init_schema.sql` 建表；表与列**必须写中文注释**。

### 4.1 `proxy_node` — 节点池

FRONT（第一跳出国）与 LAND（第二跳落地）两类节点同表，用 `role` 区分。

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | 主键 |
| `name` | VARCHAR(64) UNIQUE | 运维可读名，如 `US-机场-01`、`LAND-东京-03` |
| `role` | VARCHAR(16) | `FRONT` / `LAND` |
| `protocol` | VARCHAR(16) | `TROJAN` / `SOCKS5` / `VMESS`，决定表单模板与敏感键集合 |
| `server_addr` | VARCHAR(255) | 节点地址，明文（列表页展示与搜索） |
| `port` | INT | 端口，明文 |
| `extra_config` | JSON | 非敏感的 mihomo 透传键（`sni`、`network`、`skip-cert-verify` 等） |
| `secret_cipher` | TEXT | 敏感键 JSON 的 AES-GCM 密文（见 4.4） |
| `egress_ips` | JSON | 出口 IP 集合，**仅 LAND 有值**；供客户端做出口校验 |
| `status` | VARCHAR(16) | `ENABLED` / `DISABLED`，禁用节点不可分配、不下发 |
| `remark` | VARCHAR(255) | 备注 |
| `created_at` / `updated_at` | DATETIME | — |

**出口 IP 从用户挪到落地节点**：它本来就是节点的属性。挪过去之后「换节点自动换校验 IP」，不会出现改了节点忘改 IP 的漏配。

### 4.2 `app_user` — 用户

表名用 `app_user` 而非 `user`，避开与 MySQL 自身 `user` 表/关键字的歧义。

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | 主键 |
| `subject` | VARCHAR(128) UNIQUE | Logto user id，即 JWT 的 `sub` |
| `name` | VARCHAR(64) | 姓名，用于页面展示与日志排查 |
| `role` | VARCHAR(16) | `ADMIN` / `MEMBER`，默认 `MEMBER`（见第 6 节） |
| `status` | VARCHAR(16) | `ACTIVE` / `SUSPENDED` / `REVOKED` |
| `front_node_id` | BIGINT NOT NULL | 引用 `proxy_node`（role=FRONT） |
| `land_node_id` | BIGINT NULL **UNIQUE** | 引用 `proxy_node`（role=LAND） |
| `claude_credential_cipher` | TEXT | Claude 席位长效凭据的 AES-GCM 密文 |
| `created_at` / `updated_at` | DATETIME | — |

**`land_node_id` 的唯一索引是「一人一落地出口」的兜底**：MySQL 唯一索引不约束多个 NULL，所以「尚未分配落地」可以有很多条，而「一个落地节点被两人共用」在数据库层面直接插不进去，不依赖应用层自觉。

`front_node_id` 非空意味着**必须先有至少一个 ENABLED 的 FRONT 节点才能建用户**；管理端在没有可选节点时直接提示先去节点池建节点，不给空表单。

外键约束：`front_node_id` / `land_node_id` 建 FK 指向 `proxy_node(id)`，`ON DELETE RESTRICT`——被引用的节点删不掉，删除请求由 Service 层提前拦下并给出可读错误（`410003`），不让 FK 异常裸奔到用户面前。

### 4.3 链路下发的组装

`LinkService` 拿到 `UserDto` 后：

- `front` = FRONT 节点的 `{server, port} + extra_config + 解密后的敏感键`
- `land` = LAND 节点同上
- `expectedEgressIps` = LAND 节点的 `egress_ips`
- `claudeCredential` = 解密后的席位凭据

校验链在现有三条之上补两条，全部复用 / 扩展 `BizCodeEnum`：

| 情况 | 错误码 |
|---|---|
| `subject` 不在 `app_user` 中 | `210003 ACCOUNT_NOT_ENROLLED`（已有） |
| `status != ACTIVE` | `310003 LINK_REVOKED`（已有） |
| 未分配落地节点，或落地节点 `egress_ips` 为空 | `310001 EGRESS_NOT_ASSIGNED`（已有） |
| 席位凭据为空 | `310002 CREDENTIAL_NOT_ASSIGNED`（已有） |
| 引用的节点被 `DISABLED` | `310004 NODE_DISABLED`（新增） |

### 4.4 敏感字段加密

- 算法 **AES-256-GCM**，密钥 32 字节以 Base64 存于配置项 `mintpop.crypto.key`，**由部署机环境变量注入**，不进库、不进镜像、不进 git。
- 每条密文自带随机 12 字节 IV，存储形态为 `Base64(IV || ciphertext || tag)`。
- **敏感键由协议模板定义**：`TROJAN` → `password`；`SOCKS5` → `username`、`password`；`VMESS` → `uuid`。这些键的值序列化成一个 JSON 对象后整体加密进 `secret_cipher`；其余键落 `extra_config` 明文（便于按地址排查）。管理端自由追加的未知键默认按非敏感处理。
- 加解密封装在 `crypto/CredentialCipher`，构造器注入，可在测试中替换。

## 5. 服务端架构

```
controller/admin/*          →  service/Admin*Service  →  repository/*Repository（接口）
controller/LinkController   →  service/LinkService    ↗            ↓ 实现
                                                        Mybatis*Repository ──→ mapper/*Mapper（MyBatis-Plus）
                                                                ↓                        ↓
                                                        converter/*Converter      entity/*（表映射，含密文列）
                                                                ↓
                                                        crypto/CredentialCipher
```

**加解密收口在 repository 之下**，这是本设计的关键约束：Service 层拿到的 `UserDto` / `ProxyNodeDto` 永远是明文领域对象，密文只存在于 repository 实现与 entity 里，加解密全项目只有一处。

分包（沿用 `web-project-design.md`）：

| 包 | 内容 |
|---|---|
| `entity` | `User`、`ProxyNode`——MyBatis-Plus 表映射，`@TableName(autoResultMap = true)`，JSON 列用内置 `JacksonTypeHandler` |
| `dto` | `UserDto`、`ProxyNodeDto`——服务层流转的明文对象 |
| `converter` | `UserConverter`、`ProxyNodeConverter`——entity ↔ dto，调 `CredentialCipher` |
| `mapper` | `UserMapper`、`ProxyNodeMapper`——继承 `BaseMapper` |
| `repository` | `UserRepository`、`ProxyNodeRepository` 接口 + `Mybatis*` 实现 |
| `service` | `LinkService`（已有）、`AdminUserService`、`AdminNodeService` + Impl |
| `controller` | `LinkController`（已有）、`admin/AdminUserController`、`admin/AdminNodeController` |
| `request` / `response` | 管理端出入参；响应里凭据与节点密码一律掏码 |

`UserRepository` 从只读的 `findBySubject` 扩成完整读写口。**桌面端那条路（LinkService）与管理端那条路（AdminUserService）都只依赖这个接口**，不会出现两条路径读同一张表却行为不一致。

依赖新增：`mybatis-plus-spring-boot3-starter`（版本由 `mybatis-plus-bom` 管）、`mysql-connector-j`、`flyway-core` + `flyway-mysql`；测试加 `testcontainers:mysql` 与 `testcontainers:junit-jupiter`。分页用 MyBatis-Plus 的 `PaginationInnerInterceptor`。

## 6. 认证与授权

遵循全局规范 `authz-in-business-system.md`：**Logto 只管身份，权限在本系统的库里。**

- **Logto 侧不建角色、不定义业务 scope**，配置与现在完全一样（issuer + API resource audience）。
- 服务端从 JWT 只取 `sub`（以及签名 / issuer / audience 校验），**再用 `sub` 查 `app_user.role`**。
- 自定义 `JwtAuthenticationConverter`（注入 `UserRepository`）把库里的 `role` 映射成 `ROLE_ADMIN` / `ROLE_MEMBER` 权限，鉴权点保持声明式：

```java
.requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
.requestMatchers("/api/link/**").authenticated()
.anyRequest().denyAll()
```

- `sub` 不在 `app_user` 中的 token：无任何权限。访问 `/api/admin/**` 得 403；访问 `/api/link/**` 仍走现有的 `ACCOUNT_NOT_ENROLLED` 业务错误，行为不变。
- **首个管理员手动改库**：`UPDATE app_user SET role = 'ADMIN' WHERE subject = '<你的 Logto user id>';`。这一步写进 `deploy/README.md`。
- 每个请求多一次按唯一索引的查询，几十人规模不构成问题；确有需要再加本地缓存，本期不做。
- **管理端不提供 `role` 的编辑入口**：授予 / 撤销管理员一律改库。页面上能改角色意味着管理员可以把自己降级、或给任意人提权且无从追溯（本期没有操作日志），收益远低于风险。

## 7. 管理端 API

统一 `ApiResponse<T>` 与 `BizCodeEnum`，错误码开新号段 **41 管理端**（11 通用 / 21 认证 / 31 链路 / 41 管理端）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/users` | 分页列表，支持按姓名 / subject 关键字搜索；含前置与落地节点名、出口 IP；凭据掏码 |
| POST | `/api/admin/users` | 新建 |
| PUT | `/api/admin/users/{id}` | 更新；**凭据字段留空表示不改**，非空才整条覆盖 |
| DELETE | `/api/admin/users/{id}` | 删除（物理删除，落地节点随之释放） |
| GET | `/api/admin/nodes` | 节点列表，可按 `role` 过滤；LAND 节点带「已分配给谁」 |
| POST | `/api/admin/nodes` | 新建 |
| PUT | `/api/admin/nodes/{id}` | 更新；密码等敏感键留空表示不改 |
| DELETE | `/api/admin/nodes/{id}` | 删除；被用户引用时拒绝 |

新增错误码：

| 码 | 含义 |
|---|---|
| `410001` | 节点不存在 |
| `410002` | 该落地节点已被其他用户占用 |
| `410003` | 该节点仍被用户引用，无法删除 |
| `410004` | 该 Logto 用户已存在 |
| `410005` | 节点角色与用途不符（如把 FRONT 节点分配为落地出口） |

## 8. 管理端前端 `apps/admin`

**Vue 3 + TypeScript + Vite + Element Plus + Pinia + vue-router**，`@logto/browser` 走 SPA 的 PKCE 授权码流程，access token（audience 为现有 API resource）以 Bearer 调 `/api/admin`。

页面三块：

1. **登录 / 回调页**——非 ADMIN 的用户登录成功后会被服务端 403，页面明确提示「无管理权限」，不伪装成网络错误。
2. **用户列表**——分页 + 关键字搜索；行内展示状态、角色、前置节点、落地节点与出口 IP；新建 / 编辑走抽屉表单。凭据显示为掏码占位，留空即不改。
3. **节点池**——FRONT / LAND 两个 tab。表单为「协议模板 + 自由键值对」：选 `TROJAN` 出 server / port / password / sni 等常用键，另允许追加任意键值对透传给 mihomo，保持终端 spec 里「协议字段千变万化，不做强类型化」的取向。LAND tab 每行显示「已分配给谁 / 未分配」。

按 `global-reachability.md`：字体用系统字体栈（零网络请求；原定的 `@fontsource` 自托管方案因中文字体包体 81 MB 而放弃，理由见二期计划），Element Plus 从 npm 打进产物，**页面不出现任何外链 CDN**。`package.json` 只留依赖不留 scripts，命令收口到根 `mise.toml`。

## 9. 构建、CI/CD 与部署

**mise 任务**（根 `mise.toml`，按现有分组插入）：`install-admin`（带 `--frozen`）、`run-admin`、`build-admin`、`lint-admin`、`test-admin`、`image-admin`、`release-admin`；顶层 `install` 的 `depends` 加一项。

**镜像**：`apps/admin/Dockerfile`——debian-slim + curl 装 mise → `mise install node pnpm` → `mise run install-admin --frozen` → `mise run build-admin` → nginx 运行阶段（SPA history fallback）。配白名单式 `apps/admin/Dockerfile.dockerignore`，context 取仓库根。

**工作流**：

- `quality.yml` 加第三个 job「管理端质量门禁」（install → lint → test）。`ci-repo.yml` 与发版链路共用这一份定义。
- 新增 `release-admin.yml`（监听 `admin-v*`，`workflow_dispatch` 逃生口，`needs: quality` 后 build/push，最后建 GitHub Release）。
- `action-notify.yml` 的 `workflows` 列表加 `Release Admin`。
- 服务端发版已有质量门禁（`release-server.yml` 的 `image` job `needs: quality`），无需改动。

**部署**（`deploy/docker-compose.yml`）：

- 加 `admin` service（nginx，`127.0.0.1:${ADMIN_PORT:-8082}:80`，带 healthcheck）。
- `server` 新增环境变量：`SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`（外置 MySQL）、`MINTPOP_CRYPTO_KEY`。口令与密钥走部署机上的 `.env`（gitignore），compose 里只写 `${VAR}` 引用。
- 挂载的 `application-prod.yaml` 从此只剩 OIDC 配置与 `ttl-seconds`。
- 宿主 nginx 同域分路径：`/` → admin，`/api` → server。前端因此无需 CORS，Logto 回调也只有一个源。

## 10. 测试策略

- **repository 层用 Testcontainers + MySQL 做真集成**：JSON 列与唯一索引用 H2 模拟不真，而「落地节点唯一约束」正是我们指望数据库兜底的东西，必须在真 MySQL 上验证。代价是 CI 的 server job 多拉一个容器（GitHub runner 自带 docker）。
- **service / controller 层沿用现有风格**：mock repository，不碰数据库；管理端接口测试覆盖 403（非 ADMIN）与 401（无 token）两条路径。
- **`CredentialCipher`** 单测：加解密往返、密文每次不同（随机 IV）、篡改密文必须解密失败。
- **改名回归**：桌面端 Rust 侧只改类型名，现有测试守住 `"ACTIVE"` / `"REVOKED"` 反序列化。
- **前端** Vitest 测表单校验与「凭据留空不改」这条容易写错的逻辑；不做 E2E。

## 11. 上线步骤

1. 在外置 MySQL 上建库并配好账号；服务端启动时 Flyway 自动建表。
2. 用管理端（或首次用 curl）录入：FRONT 节点 1 个、各 LAND 节点、各用户。**数据量只有几十条，手工录入，不写自动迁移脚本。**
3. 手动把自己的账号改成 ADMIN（第 6 节的 SQL）。
4. 从 `application-prod.yaml` 删除 `mintpop.link.employees` 与 `mintpop.link.front`。
5. 发布新版服务端与管理端镜像，`mise run up`。
6. 用桌面端登录验证链路下发正常；核对出口 IP 与库中 `egress_ips` 一致。

**回滚**：保留切换前的 `application-prod.yaml` 与旧版服务端镜像 tag，回滚即回退镜像 + 恢复配置文件。数据库表留着不影响旧版本运行。

## 12. 风险与验证

实现前逐条验证：

| # | 风险 | 验证方式 | 退路 |
|---|---|---|---|
| 1 | 自定义 `JwtAuthenticationConverter` 里注入 `UserRepository` 查库，是否与 Spring Security 的过滤器初始化顺序冲突（循环依赖） | 起服务后用有效 token 打一次 `/api/admin/users`，确认权限映射生效 | 改在 `AuthenticationProvider` 之后用一个自定义 filter 补权限，或在 Service 层用 `@PreAuthorize` + 自定义 `PermissionEvaluator` |
| 2 | MyBatis-Plus 的 `JacksonTypeHandler` 对 MySQL JSON 列的读写是否需要额外配置 | 写一条含 `extra_config` 与 `egress_ips` 的记录并读回比对 | 改用 `TEXT` 列存 JSON 字符串，自己序列化 |
| 3 | Flyway 对 MySQL 8 需要 `flyway-mysql` 模块，与 Spring Boot 3 的版本匹配 | `mise run test-server` 时 Testcontainers 上跑一次迁移 | 版本对不上则先手工执行 DDL，Flyway 延后引入 |
| 4 | `land_node_id` 唯一索引与「多个 NULL」的行为 | 插两条 `land_node_id = NULL` 的用户，确认都能插入；再插两条同一个非空值，确认第二条报错 | 若行为不符预期，改为应用层校验 + 唯一索引改成组合索引 |
| 5 | Logto SPA 在同域子路径下的回调与静默刷新是否正常 | 本地起 nginx 模拟 `/` + `/api` 分路径，跑一次完整登录 | 改用独立子域名 + 服务端开 CORS |

## 13. 分期实现

设计是一个整体，实现拆两期，各自一份计划，一期验完再动二期：

- **一期（服务端）**：改名 `User`、Flyway 建表、MyBatis-Plus 接入、加解密、`UserRepository` 换实现、`LinkService` 适配、`/api/admin/**` 与 ADMIN 鉴权、compose 与配置改造。**做完用 curl 就能完成全部运维动作，现网即可脱离 YAML 运行。**
- **二期（管理端前端）**：`apps/admin` 工程与三个页面、镜像、CI/CD、宿主反代接入。

## 14. 后续子项目

1. **使用审计与报表**：心跳 / 下发 / 出口校验失败的事件落库，按人按时间出报表
2. **管理端操作日志**：谁在什么时候改了什么，配合审计
3. **凭据轮换运维**：批量轮换、到期提醒
