# 部署

拉取 GHCR 上已发布的镜像运行。服务端、管理端与官网是三个独立发版的组件，镜像分别由 `server-v*`、`admin-v*` 与 `website-v*` tag 触发的发版流水线构建并推送，部署机不做构建。

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

5. **建 Logto 的传统 Web 应用**：

   在 Logto 控制台新建一个 **Traditional Web** 类型的应用。登录（无论是管理端网页还是桌面端）现在统一由**服务端**发起并用这一个应用做 authorization code 交换，不再需要像过去那样为桌面端、管理端、API 分别建应用——原来的 Native 应用、SPA 应用与 API Resource 都不再需要，可以在控制台删掉。要填两个地址：

   | 项 | 值 |
   |---|---|
   | Redirect URI | `https://<管理端域名>/auth/callback`、`https://<主站域名>/auth/callback` |
   | Post sign-out redirect URI | `https://<管理端域名>/auth/logout/callback` |

   > 两类地址都按「请求实际到达的域名」动态展开：登录回调 `{baseUrl}/auth/callback` 管理端与主站（桌面端登录流走主站域名）各一条；登出回跳指向服务端的 `/auth/logout/callback` 中转端点（Logto 清完 IdP 会话回到它，再回当前域名首页），目前只有管理端网页有登出入口，登记管理端域名这一条即可。Logto 只接受已登记的地址，没登记对应跳转会被 Logto 拒绝、页面停在它的报错页。

   把 App ID 和 App Secret 记下来，都填进第 4 步的 `application.yml`：App ID 填 `spring.security.oauth2.client.registration.logto.client-id`，App Secret 填同级的 `client-secret`。

   > 本地开发管理端时（`mise run run-admin`，Vite 默认端口 5173），需要在这个 Traditional Web 应用**额外追加**回调地址 `http://localhost:5173/auth/callback` 与登出回跳 `http://localhost:5173/auth/logout/callback`——本地起的 Vite dev server 会把 `/api`、`/auth`、`/oauth2` 代理转发给本机服务端（`mise run run-server`），登录整段流程与线上一致，只是回调域名换成本机。

   > ⚠️ **部署约束**：管理端与 API 必须**同源**（同协议 + 同域名 + 同端口）分路径部署。这件事由**管理端容器内的 nginx** 完成：它把 `/api`、`/auth`、`/oauth2` 反代到 server 容器（compose 内网），其余路径服务管理端静态站——宿主入口只需按 Host 把整个域名转给管理端容器即可，见下文「对外暴露」。管理端的请求是同源相对路径（`fetch("/api/...")`、登录入口 `/oauth2/authorization/logto`），换成 `admin.x.com` 与 `api.x.com` 这种跨子域形态，接口地址与登录入口都不再同源，会话 Cookie 也带不过去。接口前缀 `/api` 由服务端路由固定，已直接写在管理端代码里，部署侧无需、也没有地方配置它。

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

   d. 插入第一个节点（节点池管理不要求先有管理员，但密码字段仍必须由服务端加密写入，不能手写密文）：

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

## 授予或撤销管理员

**管理端不提供改角色的入口**——能在页面上提权，就等于给自己留了后门，且本期没有操作日志可追溯。授予管理员一律改库：

```sql
UPDATE app_user SET role = 'ADMIN' WHERE subject = '<Logto user id>';
UPDATE app_user SET role = 'MEMBER' WHERE subject = '<Logto user id>';  -- 撤销
```

改完立即生效（每次请求都会重新查库取角色，无缓存）。

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
| 从订阅导入节点 | 管理端节点池页提供『从订阅导入』功能，支持从机场订阅链接批量导入第一跳节点，导入节点按订阅链接自动归组，可重新拉取更新 |
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
| `TZ` | `UTC` | 服务端容器时区，仅影响日志时间显示。业务时间全链路按 UTC 存取、按查看者本地时区显示，与本变量无关 |

## 备份

要备份两样东西，**且必须分开存放**：

1. **数据库**（`mysqldump lane`）——里面的凭据是密文。
2. **`lane.crypto.key`（在部署机仓库根的 `application.yml` 里）**——没有它，数据库备份里的凭据无法解开。

两者存在同一处等于加密白做。

> `lane.auth.session-secret` 不属于上面这两样、也不需要备份：它只签自签会话 token，丢失或更换的后果是**全员下线**（无数据损失，重新登录一次即可拿到新会话），与 `lane.crypto.key` 丢失会让密文永久解不开是两种截然不同的后果，不要混为一谈。

## 对外暴露

前端容器端口**都只绑 `127.0.0.1`**，公网访问不到；server **不映射宿主端口**，只经容器网络被管理端与官网反代访问。对外入口是宿主机上**已有的反代**（OpenResty/nginx，与本机其它站点共用），它**只按 Host 分流**、每个站点一条 `location /`——API 的路径拆分不在这一层做：管理端与官网容器内的 nginx 各自把 `/api`、`/auth`、`/oauth2` 反代到 server（compose 服务名 `server:8080`），因此各域名上的 API 调用天然同源，前端不需要 CORS。

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

> 注意：Docker 自己写的 iptables `DOCKER` 链在 ufw 规则之前，`ufw deny <端口>` 拦不住已发布的容器端口。因此「不对外暴露」只能靠绑定地址收口，不能指望防火墙——这就是端口写成三段式 `127.0.0.1:<宿主端口>:<容器端口>` 的原因。

## 发版顺序（桌面端与服务端）

> ⚠️ **登录体系重构的上线配套**：本文档描述的是新协议（服务端自签会话 + Logto 传统 Web 应用）。桌面端与管理端均已跟进新登录协议，三期已收官；发版时注意三个组件版本配套。旧的 `GET /api/client-config` 端点已随本次重构下线——若线上还有跑旧协议的桌面端或管理端，升级服务端会让它们的登录立即失效，升级前请确认桌面端/管理端已同步跟进，不要单独抢先上线服务端。

服务端上线前确认部署机仓库根的 `application.yml` 里已配：

- `spring.security.oauth2.client.registration.logto.client-id` 与 `client-secret`（Logto 传统 Web 应用的凭据）
- `spring.security.oauth2.client.provider.logto.issuer-uri`（形如 `https://<租户>.logto.app/oidc`）
- `lane.auth.session-secret` 与 `lane.crypto.key`（两个本地生成的密钥）
- `spring.datasource.*`（外置 MySQL 连接）
