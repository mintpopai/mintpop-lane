# 部署

拉取 GHCR 上已发布的镜像运行。服务端、管理端、官网与控制台是四个独立发版的组件，镜像分别由 `server-v*`、`admin-v*`、`website-v*` 与 `console-v*` tag 触发的发版流水线构建并推送，部署机不做构建。

> 桌面端已拆分为独立仓库 [`mintpopai/mintpop-lane-desktop`](https://github.com/mintpopai/mintpop-lane-desktop)（保留完整 git 历史），安装包由其 GitHub Releases 分发；本仓官网（`apps/website`）的下载页即从那里拉取最新版本直链。

用户与节点数据都在外置 MySQL 里，日常运维（加人、停用、换落地出口、换席位凭据）走 `/api/admin/**` 接口，**不需要改配置文件、不需要重启服务**。

## 首次部署

> 以下 1～7 步的命令都在**仓库根目录**执行；部署所需的两个文件（`application.yml`、可选的 `.env`）也都放仓库根、与 `docker-compose.yml` 同目录，均已被 `.gitignore` 排除。
>
> **前置条件**：外置 MySQL 需为 **8.0 及以上**（本项目开发与测试用的是 8.4）。建库语句用了 `utf8mb4_0900_ai_ci` 排序规则，表结构里也有 JSON 列，这两者都要求 8.0+；5.x 会在建库这一步直接报错。

1. **登录 GHCR**（镜像为私有包时必须）：

   ```bash
   docker login ghcr.io -u <你的 GitHub 用户名>
   # 密码用一个具备 read:packages 权限的 PAT
   ```

2. **在外置 MySQL 上建库**（表由 Flyway 在服务启动时自动创建）：

   ```sql
   CREATE DATABASE lane DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'lane'@'%' IDENTIFIED BY '<口令>';
   GRANT ALL PRIVILEGES ON lane.* TO 'lane'@'%';
   ```

3. **（可选）放置 `.env`**：镜像版本、宿主端口、容器时区都有默认值（见下文「可调参数」），要覆盖时在仓库根建 `.env` 写入对应变量即可；全用默认值就跳过这一步。

4. **放置服务端配置**：把 `apps/server/config/application.example.yml` 复制到仓库根改名 `application.yml`，照注释填入全部真实值——外置 MySQL 连接、Logto issuer 与传统 Web 应用的 App ID，以及两个本地生成的密钥：

   ```bash
   cp apps/server/config/application.example.yml ./application.yml
   openssl rand -base64 32   # 把输出填进 lane.crypto.key
   openssl rand -base64 32   # 再生成一个，填进 lane.auth.session-secret
   ```

   由 compose 以只读卷把它挂进容器的 `/app/config/`，不进镜像。该文件**含数据库口令与密钥**，已在 `.gitignore` 中，严禁入库。

   > ⚠️ `lane.crypto.key` 用来加密席位凭据与节点密码。**丢失或更换 = 库里所有密文永久解不开**，必须重录全部凭据。请与数据库口令分开备份。
   >
   > `lane.auth.session-secret` 是自签会话 token 的 HS256 签名密钥（至少 32 字节）。换掉它会让所有已登录会话（含管理端网页与桌面端钥匙串里的）立即失效——这也是一种应急踢下线手段。
   >
   > 文件里的 `client-secret` 不是本地生成的，而是**第 5 步**在 Logto 控制台建好传统 Web 应用后从控制台复制过来的，先留占位，走到那一步再填。
   >
   > `lane.front-tuning` 一段是**本期唯一会改变下发给全部在线客户端内容的开关**，也是**唯一的回退手段**：它按 mihomo 协议名（key 是订阅里的真实 `type`，如 `anytls`）给前置节点的下发参数打一层覆盖，用于治理「anytls 空闲即断连接池」——关键是 `min-idle-session: 1`，让客户端在后台维持一条热连接，握手发生在后台而不是用户请求的关键路径上。表里没有的协议原样透传，**加机场不用改代码**；只覆盖前置（FRONT），落地不走这条路径；用户订阅里已显式配了同名键时以覆盖表为准。**整段留空即完全恢复现状**——出事时先清空这一段，不需要回滚镜像。⚠️ 表里的数值未经实测（按 mihomo 官方文档做的保守调整），拉长空闲超时理论上可能触发机场的异常长连接限速；若出现「连接建立后很快被掐」的新症状，先把 `idle-session-timeout` 调回 `30` 再排查。

   > `notify.feishu` 一段**可选**：填上飞书群自定义机器人的 webhook 地址与签名密钥后，落地节点的出口 IP 一旦变更就会推一张卡片到群里——无论是在管理端改的（含「检测」后一键回填），还是服务端定时巡检（间隔 `egress-check.interval`，默认 10 分钟）发现实际出口与登记不一致后自动回填的，都在库改完之后再推。出口时区跟着出口 IP 走：巡检回填时服务端按新 IP 解析时区（GeoIP 走 ipwho.is，服务端需能访问它）与 IP 一起写回，解析不到就本轮跳过下轮再试；管理端改 IP 时表单会联动预填时区，管理员也可以自己改，以提交值为准。探测不通只记日志。不填 webhook 则只是不推消息，巡检与自动回填照常。机器人在飞书群设置 → 群机器人 → 添加「自定义机器人」处创建，安全设置勾选「签名校验」。

5. **建 Logto 的传统 Web 应用**：

   在 Logto 控制台新建一个 **Traditional Web** 类型的应用。登录（无论是管理端网页还是桌面端）现在统一由**服务端**发起并用这一个应用做 authorization code 交换，不再需要像过去那样为桌面端、管理端、API 分别建应用——原来的 Native 应用、SPA 应用与 API Resource 都不再需要，可以在控制台删掉。要填两个地址：

   | 项 | 值 |
   |---|---|
   | Redirect URI | `https://<管理端域名>/auth/callback`、`https://<主站域名>/auth/callback`、`https://console.lane.mintpop.ai/auth/callback` |
   | Post sign-out redirect URI | `https://<管理端域名>/auth/logout/callback`、`https://console.lane.mintpop.ai/auth/logout/callback` |

   > 两类地址都按「请求实际到达的域名」动态展开：登录回调 `{baseUrl}/auth/callback` 管理端、主站（桌面端登录流走主站域名）与控制台各一条；登出回跳指向服务端的 `/auth/logout/callback` 中转端点（Logto 清完 IdP 会话回到它，再回当前域名首页），管理端网页与控制台都有登出入口，各登记一条即可，主站没有登出入口不需要登记。Logto 只接受已登记的地址，没登记对应跳转会被 Logto 拒绝、页面停在它的报错页。

   把 App ID 和 App Secret 记下来，都填进第 4 步的 `application.yml`：App ID 填 `spring.security.oauth2.client.registration.logto.client-id`，App Secret 填同级的 `client-secret`。

   > 本地开发管理端时（`mise run run-admin`，端口 6201），需要在这个 Traditional Web 应用**额外追加**回调地址 `http://localhost:6201/auth/callback` 与登出回跳 `http://localhost:6201/auth/logout/callback`——本地起的 Vite dev server 会把 `/api`、`/auth`、`/oauth2` 代理转发给本机服务端（`mise run run-server`，端口 6200），登录整段流程与线上一致，只是回调域名换成本机。本地开发控制台时（`mise run run-console`，端口 6203）同理，追加 `http://localhost:6203/auth/callback` 与 `http://localhost:6203/auth/logout/callback`。
   >
   > **本地端口固定、不许漂移**：本项目本地开发占用 6200 段——server `6200`、admin `6201`、website `6202`、console `6203`、官网预览 `6205`、桌面端（mintpop-lane-desktop）`6204`。前端全部用 strictPort，服务端端口写在本地 `apps/server/config/application.yml` 的 `server.port`（照示例配置里的注释解开），端口被占用时直接报错退出，**不会也不允许自己换端口**；遇到冲突先找出占用进程（`lsof -nP -iTCP:<端口> -sTCP:LISTEN`）处理掉，而不是改端口——Logto 里登记的本地回调、前端代理目标、桌面端默认服务端地址都依赖这几个固定值。

   > ⚠️ **部署约束**：管理端、控制台与 API 必须**各自同源**（同协议 + 同域名 + 同端口）分路径部署。这件事由**管理端与控制台容器各自内置的 nginx** 完成：都把 `/api`、`/auth`、`/oauth2` 反代到 server 容器（compose 内网），其余路径服务各自的静态站——宿主入口只需按 Host 把对应域名转给对应容器即可，见下文「对外暴露」。管理端与控制台的请求都是同源相对路径（`fetch("/api/...")`、登录入口 `/oauth2/authorization/logto`），换成 `admin.x.com`/`console.x.com` 与 `api.x.com` 这种跨子域形态，接口地址与登录入口都不再同源，会话 Cookie 也带不过去。接口前缀 `/api` 由服务端路由固定，已直接写在管理端与控制台代码里，部署侧无需、也没有地方配置它。

6. **拉起服务**：

   ```bash
   mise run up
   ```

7. **录入初始数据**（首次没有管理员，用「登录一次 + 改库提权」配合完成——**新会话模型下不能再拿 Logto 的 access token 当 Bearer**，服务端只认自签会话 token）：

   a. 浏览器访问 `https://<你的域名>/oauth2/authorization/logto`，用一个已在 Logto 注册的账号登录一次。登录成功即触发服务端唯一的建档入口——库里自动出现一行 `role=MEMBER` 的新用户，浏览器也已经拿到会话 Cookie（`lane_session`）。登录后 302 到管理端地址此时会落在 `/forbidden`（角色还是 `MEMBER`，尚未提权）属正常，不影响建档与 Cookie 已经生效。

   b. 库里把这个账号提为管理员。**首个管理员只能改库产生**——这是设计决定，管理端本就不提供改角色的入口（见下一节「授予或撤销管理员」）：

   ```sql
   UPDATE app_user SET role = 'ADMIN' WHERE email = '<你刚登录用的邮箱>';
   ```

   c. 从浏览器开发者工具（Application → Cookies，找 该域名下的 `lane_session`）复制它的值——这就是自签会话 token，Bearer 头与 Cookie 两种载体服务端都认。先验证登录态与刚才的提权：

   ```bash
   curl https://<你的域名>/api/me -H "Authorization: Bearer <lane_session 的值>"
   ```

   能拿到你自己的资料且 `role` 已是 `ADMIN`，说明会话可用、提权生效。

   d. 插入第一个节点（节点管理不要求先有管理员，但密码字段仍必须由服务端加密写入，不能手写密文）：

   ```sql
   INSERT INTO proxy_node (name, role, protocol, server_addr, port)
   VALUES ('FRONT-1', 'FRONT', 'TROJAN', 'us.example.com', 443);
   ```

   > 节点的密码字段 `secret_cipher` 是密文，**不要手写**——插入时先留空，随后用管理接口补齐，由服务端加密写入。

   e. 用第 c 步拿到的 `lane_session` 值调用管理接口补齐节点密码。**`PUT /api/admin/nodes/{id}` 是整体覆盖式更新，不是局部补丁**——`name`/`role`/`protocol`/`serverAddr`/`port`/`status` 都是必填校验字段，必须连同 `secret` 一起原样提交，只传 `secret` 会被参数校验挡回来（`110001`）：

   ```bash
   curl -X PUT https://<你的域名>/api/admin/nodes/1 \
     -H "Authorization: Bearer <lane_session 的值>" \
     -H "Content-Type: application/json" \
     -d '{
       "name": "FRONT-1",
       "role": "FRONT",
       "protocol": "TROJAN",
       "serverAddr": "us.example.com",
       "port": 443,
       "status": "ENABLED",
       "secret": { "password": "<真实密码>" }
     }'
   ```

   f. 之后的一切（加落地节点、加用户、分配落地出口、录席位凭据）都走管理接口，继续用同一个 `lane_session` 值做 Bearer——网页会话有效期见 `lane.auth.web-session-ttl`（默认 7 天），过期后回到第 a 步重新登录一次即可拿到新值。

## 支付配置（Stripe）

控制台的自助购买套餐功能依赖 Stripe，未配置时该入口自动禁用，不影响其它功能（管理端、官网、桌面端登录与节点管理照常可用）。

1. 在 [Stripe Dashboard](https://dashboard.stripe.com) 取 secret key（`sk_` 开头）与 publishable key（`pk_` 开头），填进 `application.yml` 的 `payment.stripe.secret-key` 与 `publishable-key`。

2. Dashboard → Developers → Webhooks 建一个端点，地址填 `https://console.lane.mintpop.ai/api/v1/payment/webhook/stripe`，只订阅 `payment_intent.succeeded` 与 `payment_intent.payment_failed` 这两个事件，把端点详情页给出的 `whsec_` 开头的签名密钥填进 `webhook-secret`。

3. 不配置 `payment.stripe` 这一段（或留空 secret-key）时，控制台的购买入口直接禁用，购买按钮禁用并提示支付暂未开放；其余功能（登录、查看订阅、桌面端下载等）不受影响。

4. `payment.stripe.product-code`：写入每笔 PaymentIntent 的 `metadata.product`，同一 Stripe 账号被多个业务线共用时，webhook 据此只认领属于本业务（`lane`）的事件，避免误处理其它业务线打进同一 webhook 端点的通知。

5. `payment.stripe.statement-descriptor-suffix`：显示在用户银行账单上的商户描述符后缀（如配成 `LANE`，账单显示为 `MINTPOP* LANE`），留空则不传该字段、由 Stripe 账号的默认描述符显示。

6. **本地联调**：安装 [Stripe CLI](https://stripe.com/docs/stripe-cli) 并执行一次 `stripe login`，然后跑 `mise run webhook-listen`，把它打印出来的 `whsec_` 填进本机 `application.yml` 的 `webhook-secret`（与线上 Dashboard 那把不是同一把，仅本地联调用）。测试支付用 Stripe 测试卡号 `4242 4242 4242 4242`（任意未来到期日、任意 CVC）；微信支付 / 支付宝在测试模式下点击后会跳到 Stripe 提供的模拟扫码页，无需真实账号即可走完整流程。

## 套餐图上传（可选）

管理端的套餐表单可以直接上传套餐图，图存在 Cloudflare R2、由 `assets.lane.mintpop.ai` 对外提供。
**不配也能用**——上传按钮会提示「图片存储未配置」，手填图片地址那条路照常可用。

要启用，在 `application.yml` 里填 `storage.r2` 五项（见 `apps/server/config/application.example.yml`）：
桶名 `mintpop-lane-assets`，自定义域 `assets.lane.mintpop.ai`，Access Key 由 R2 API Token 生成。

⚠️ **宿主机 OpenResty 那层也要放行请求体**：镜像里管理端 nginx 的 `/api/` 已设
`client_max_body_size 6m`，但请求先过宿主入口，那里若仍是默认的 1 MB，上传大图会被 413 挡下，
管理端提示「文件太大，超出服务器允许的上传上限」。在宿主 OpenResty 对应 server 块里同样放到 `6m`。

⚠️ `assets.lane.mintpop.ai` 是二级子域，Universal SSL 的通配符覆盖不到，依赖已开通的
ACM + Total TLS，且**该 DNS 记录必须是橙云代理**。上线后验一次：

```bash
curl -o /dev/null -w '%{http_code}\n' https://assets.lane.mintpop.ai/<任一已上传对象键>
```

## 用户自助购买后如何开通

控制台自助购买套餐、支付成功后，订阅记录已按套餐自动建出，但**起止期需要管理员手动开通**（无自动开通流程，避免异常支付状态下误开通）：

1. 飞书群收到「新订单已支付，待开通」卡片通知，带买家邮箱、套餐、订单号与分配号。
2. 管理端「用户列表」页用买家邮箱搜索，进入该用户的详情页。
3. 详情页能看到一条带「待开通」徽标的订阅，备注里有对应订单号，与飞书卡片一一对应。
4. 点「编辑」，填入起期并保存即完成开通——止期按套餐时长自动计算，无需手填。
5. 按需给该用户分配落地节点、签发席位凭据（见「日常操作」一节的管理接口）。

> 二期上线前需核对桌面端仓库 [`mintpop-lane-desktop`](https://github.com/mintpopai/mintpop-lane-desktop) 对 `/api/me` 里 `startsAt` / `endsAt` 为 `null` 的处理——待开通的订阅会下发 `null`，桌面端需能正确展示「待开通」态而不是崩溃或误判为已过期。

## 授予或撤销管理员

**管理端不提供改角色的入口**——能在页面上提权，就等于给自己留了后门，且本期没有操作日志可追溯。授予管理员一律改库：

```sql
UPDATE app_user SET role = 'ADMIN' WHERE email = '<用户邮箱>';
UPDATE app_user SET role = 'MEMBER' WHERE email = '<用户邮箱>';  -- 撤销
```

> 系统里没有「用户名」这一概念，**邮箱就是用户的唯一标识**（`app_user.email` 上有唯一索引），定位人一律用它。`subject`（Logto user id）只是登录时找档案用的内部键，不必在运维场景里记。

改完立即生效（每次请求都会重新查库取角色，无缓存）。

## 运营商展示名

管理端「链路健康」矩阵与飞书告警文案里的运营商名字取自 `asn_org` 表。这张表只在**首次**见到某个 ASN 时记一行——服务端处理心跳上报时反查 ASN，顺手把上游（ipwho.is）当时给的组织名写进去，**之后再也不覆盖**：上游对同一个 ASN 的文案会漂（今天 `China Telecom`、明天 `CHINANET-BACKBONE`），让它每次都盖一遍会让同一家运营商的名字在页面上跳来跳去。

因此 `asn_org` 只是展示用的旁路数据，**管理端不提供编辑入口**（与「授予或撤销管理员」同一个理由：这类低频动作不值得一个页面，也没有操作日志可追溯）。改名或补名一律改库：

```sql
UPDATE asn_org SET org_name = 'China Telecom' WHERE asn = 'AS4134';
-- 首次见到某 ASN 时若上游没给名字，矩阵与告警会显示 AS 号；补一行即可
INSERT IGNORE INTO asn_org (asn, org_name, first_seen_at) VALUES ('AS4134', 'China Telecom', UTC_TIMESTAMP());
```

> 运营商维度的**键始终是 ASN**，名字只是贴上去的标签——改名不影响矩阵分组、也不影响告警去重，页面刷新即生效（每次查询都重读这张表，无缓存）。名字缺失时矩阵退回显示 AS 号本身，**这一列不会被藏掉**；`asn` 显示成「未知运营商」是另一回事——那是这批样本的 ASN 反查全失败，补 `asn_org` 也补不出来。`org_name` 列宽 64 字符，超长会被服务端截断。

## 第一跳：机场与机场订阅

第一跳（出国节点）不再由管理员手工新建或按故障域分组，而是按**机场订阅**批量导入与分配：

1. **建机场**：管理端「线路 → 机场订阅」页点「新建机场」（名字、官网地址、备注），代表一家上游服务商；每家机场在该页占一个页签。
2. **导入订阅**：在该机场的页签上点「导入订阅」（所属机场已预选），贴订阅链接、填购买账号与订阅声明的带宽（Mbps）。服务端拉取并自动导入订阅里的美国节点（节点名带 🇺🇸、[US] 或「美国」），非美国节点与「剩余流量」这类信息条目一律略过；点进订阅详情页可看到它的节点，并可随时「重新拉取」同步最新参数。
3. **给用户分配**：在用户详情页点「自动分配」，按场景负载算法整份重算该用户的第一跳列表（主用 + 备用）；不再需要挑具体节点。「取消分配」清空该用户的第一跳，需二次确认。

容量与分配规则：

- 每个用户的**主用**按「每人带宽」计一个名额（默认 **20 Mbps**），一个订阅的主用容量 = 订阅带宽 / 每人带宽（向下取整）。
- 每个用户**最多分到「每人机场数」家不同的机场**（默认 **3 家**；同一家机场的多个订阅只算一家），保证单一机场出问题时还有其它机场兜底。
- **备用不占容量**：备用名额不受上面的主用容量限制。
- 每人带宽与每人机场数（以及地区）在管理端「全局配置」页可调。**任一项改动，服务端都会先重新拉取全部订阅，再为所有用户重算线路**（保存前页面会做容量预检，名额不足时不允许确认）；客户端在下一次心跳（60 秒内）自动热更新，不需要重启。「全局配置」页也可手动点「重算全部线路」。
- 订阅**每 5 分钟**自动对齐一次节点集合（新增入库、消失删除、匹配上的原地更新）；第一跳节点没有启停状态，拉到哪些当前地区的节点就用哪些。订阅拉取经 3 次重试仍失败时，每 5 分钟推一次飞书，直到人处理。

上线顺序（三者必须按序，不要跳步）：**先发桌面端** → **再部署服务端**（本功能的数据库迁移会清空旧的第一跳分配数据，用户在下一次重新分配前没有第一跳）→ **再建机场、导入订阅，并给用户逐个点「自动分配」**。

## 日常操作

| 操作 | 命令 |
|---|---|
| 启动（服务端 + 管理端 + 官网） | `mise run up` |
| 停止 | `mise run down` |
| 查看日志（需在仓库根执行） | `docker compose logs -f server` / `docker compose logs -f admin` / `docker compose logs -f website` |
| 健康检查 | `docker compose exec server wget -qO- http://127.0.0.1:8080/actuator/health`（server 不映射宿主端口） / `curl -I 127.0.0.1:8082/` / `curl -I 127.0.0.1:8083/` |

从二期起，下面这些接口日常**不需要手工调**——打开管理端页面点就行。表格保留是为了排查问题时能直接验接口。

管理接口（都需要 ADMIN 账号的会话 token）：

| 操作 | 请求 |
|---|---|
| 从订阅导入节点 | 管理端「线路 → 机场订阅」页按机场分页签，在机场页签上「导入订阅」批量导入第一跳节点；节点归入对应订阅，在订阅详情页查看与重新拉取 |
| 节点列表 | `GET /api/admin/nodes?role=LAND` |
| 新建节点 | `POST /api/admin/nodes` |
| 改节点（密码留空即不改） | `PUT /api/admin/nodes/{id}` |
| 删节点（被引用会拒绝） | `DELETE /api/admin/nodes/{id}` |
| 用户列表 | `GET /api/admin/users?keyword=&pageNo=1&pageSize=20` |
| 改用户（只改处置态与节点分配） | `PUT /api/admin/users/{id}` |
| 删用户 | `DELETE /api/admin/users/{id}` |
| 某用户的订阅列表 | `GET /api/admin/users/{userId}/subscriptions` |
| 给用户新建订阅 | `POST /api/admin/users/{userId}/subscriptions` |
| 改订阅（凭据留空即沿用原值） | `PUT /api/admin/subscriptions/{id}` |
| 删订阅 | `DELETE /api/admin/subscriptions/{id}` |
| 读 / 改全局配置（地区、每人机场数、每人带宽；改动即触发全体重算） | `GET /api/admin/settings`、`PUT /api/admin/settings` |
| 全体线路重算的最近一次状态 / 手动触发 | `GET /api/admin/front/rebuild`、`POST /api/admin/front/rebuild` |
| 重算前的容量预检 | `GET /api/admin/front/rebuild/preview` |

> 用户没有「新建」接口——账号由登录自动建档，管理端只管处置态与资源分配（见上一节）。
>
> 订阅接口的响应体不回传凭据明文，只回传 `hasCredential`（是否已录入）；改订阅时凭据字段留空即沿用原值，不会被清空。
>
> 对不存在的路由、或路径存在但 HTTP 方法用错（如给只支持 GET 的路径发 POST），本服务统一返回原生 **404**（不是标准的 405），前端因此不需要为「方法不支持」单独分支。

停用某人（终端下一次心跳即断链）：把其 `status` 改成 `SUSPENDED` 或 `REVOKED`。

## 可调参数

仓库根 `.env` 里的覆盖点（数据库连接、密钥、Logto 凭据不在这里——都在同目录 `application.yml` 里改，改完 `mise run up` 重建容器生效）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_TAG` | `latest` | 镜像版本。回滚时指定具体版本，如 `SERVER_TAG=0.1.0` |
| `ADMIN_TAG` | `latest` | 管理端镜像版本。回滚时指定具体版本，如 `ADMIN_TAG=0.1.0` |
| `ADMIN_PORT` | `8082` | 管理端的宿主监听端口 |
| `WEBSITE_TAG` | `latest` | 官网镜像版本。回滚时指定具体版本，如 `WEBSITE_TAG=0.1.0` |
| `WEBSITE_PORT` | `8083` | 官网的宿主监听端口 |
| `CONSOLE_TAG` | `latest` | 控制台镜像版本。回滚时指定具体版本，如 `CONSOLE_TAG=0.1.0` |
| `CONSOLE_PORT` | `8084` | 控制台的宿主监听端口 |
| `TZ` | `UTC` | 服务端容器时区，仅影响日志时间显示。业务时间全链路按 UTC 存取、按查看者本地时区显示，与本变量无关 |

服务端另外一个定时任务的间隔在 `application.yml` 里调（默认值见 `apps/server/config/application.example.yml`），与是否配置飞书通知无关：

- `sub-refresh.interval`（默认 `5m`）：订阅定时刷新间隔——周期性把各机场订阅的节点集合整体对齐到机场当前给出的节点（新增、更新、删除）并更新订阅自身额度；拉取失败时不动节点，只在订阅上标记失败并每轮推飞书。⚠️ **升级须知**：部署机 `application.yml` 里若已有旧示例带来的 `sub-refresh.interval: 24h`，须改为 `5m`（不改则仍按 24 小时刷新，二期的 5 分钟对齐不会生效）。

## 备份

要备份两样东西，**且必须分开存放**：

1. **数据库**（`mysqldump lane`）——里面的凭据是密文。
2. **`lane.crypto.key`（在部署机仓库根的 `application.yml` 里）**——没有它，数据库备份里的凭据无法解开。

两者存在同一处等于加密白做。

> `lane.auth.session-secret` 不属于上面这两样、也不需要备份：它只签自签会话 token，丢失或更换的后果是**全员下线**（无数据损失，重新登录一次即可拿到新会话），与 `lane.crypto.key` 丢失会让密文永久解不开是两种截然不同的后果，不要混为一谈。

## 对外暴露

管理端导入/重拉订阅、订阅尽调是同步接口，拉取最坏约 82 秒（重试 3 次），反代到服务端的超时需 ≥ 90 秒。

前端容器端口**都只绑 `127.0.0.1`**，公网访问不到；server **不映射宿主端口**，只经容器网络被管理端、官网与控制台反代访问。对外入口是宿主机上**已有的反代**（OpenResty/nginx，与本机其它站点共用），它**只按 Host 分流**、每个站点一条 `location /`——API 的路径拆分不在这一层做：管理端、官网与控制台容器内的 nginx 各自把 `/api`、`/auth`、`/oauth2` 反代到 server（compose 服务名 `server:8080`），因此各域名上的 API 调用天然同源，前端不需要 CORS。

```nginx
server {
    listen 443 ssl;
    server_name <你的域名>;

    location / {
        proxy_pass http://127.0.0.1:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

> 登录/登出完成后的回跳不需要任何配置：服务端一律用相对路径 `/` 回「当前请求所在的域名」，
> 登出的 `post_logout_redirect_uri` 也按当前请求域名动态拼出（指向 `/auth/logout/callback` 中转端点）。
> `spring.security.oauth2.client.registration.logto.redirect-uri` 配的是 `{baseUrl}/auth/callback`，
> `{baseUrl}` 同样按请求实际到达的域名展开——与第 5 步在 Logto 控制台登记的地址一一对应。

官网是**主站域名**（如 `lane.mintpop.ai`），用另一个域名（或子域名）反代到 `127.0.0.1:8083`。**桌面端 app 的 API 与登录流都打主站域名**（`/api/link/**`、`/api/auth/desktop/exchange`、`/auth/desktop/start`、`/oauth2/**`），官网容器内的 nginx 已把这三段前缀反代到 server——与管理端一样，宿主反代仍只需一条 `location /`：

```nginx
server {
    listen 443 ssl;
    server_name <官网域名>;

    location / {
        proxy_pass http://127.0.0.1:8083;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

> 官网容器内的 nginx 还自带 `/api/gh/releases` 反代（上游 GitHub API、带缓存，精确匹配优先于 `/api/` 前缀，不会转给 server），宿主反代不需要为它做任何额外配置。
>
> 桌面端登录回跳发生在主站域名（`{baseUrl}/auth/callback` 按请求到达的域名展开），因此 Logto 控制台的 Redirect URI 要**同时登记管理端与主站两个域名**的 `/auth/callback`。

控制台域名固定为 `console.lane.mintpop.ai`，与管理端一样反代到本机端口，只是端口换成 `8084`：`console.lane.mintpop.ai → 127.0.0.1:8084`。

```nginx
server {
    listen 443 ssl;
    server_name console.lane.mintpop.ai;

    location / {
        proxy_pass http://127.0.0.1:8084;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

> 注意：Docker 自己写的 iptables `DOCKER` 链在 ufw 规则之前，`ufw deny <端口>` 拦不住已发布的容器端口。因此「不对外暴露」只能靠绑定地址收口，不能指望防火墙——这就是端口写成三段式 `127.0.0.1:<宿主端口>:<容器端口>` 的原因。

## 发版顺序（桌面端与服务端）

> ⚠️ **V9 迁移（去掉用户名、邮箱升级为唯一键）的上线前置检查**：`V9__user_email_unique.sql` 会给 `app_user.email` 加唯一索引，库里若已有重复邮箱，**迁移会失败、服务起不来**。升级服务端前先在生产库上跑一次：
>
> ```sql
> SELECT email, COUNT(*) FROM app_user GROUP BY email HAVING COUNT(*) > 1;
> ```
>
> 有输出就先人工合并/清理这些账号再升级。此外该版本的 `/api/me` 与 `GET /api/admin/users` 响应都不再返回 `name` 字段，管理端需同版本一起上线。

> ⚠️ **登录体系重构的上线配套**：本文档描述的是新协议（服务端自签会话 + Logto 传统 Web 应用）。桌面端与管理端均已跟进新登录协议，三期已收官；发版时注意三个组件版本配套。旧的 `GET /api/client-config` 端点已随本次重构下线——若线上还有跑旧协议的桌面端或管理端，升级服务端会让它们的登录立即失效，升级前请确认桌面端/管理端已同步跟进，不要单独抢先上线服务端。

服务端上线前确认部署机仓库根的 `application.yml` 里已配：

- `spring.security.oauth2.client.registration.logto.client-id` 与 `client-secret`（Logto 传统 Web 应用的凭据）
- `spring.security.oauth2.client.provider.logto.issuer-uri`（形如 `https://<租户>.logto.app/oidc`）
- `lane.auth.session-secret` 与 `lane.crypto.key`（两个本地生成的密钥）
- `spring.datasource.*`（外置 MySQL 连接）
