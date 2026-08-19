# 部署

拉取 GHCR 上已发布的镜像运行。服务端与管理端是两个独立发版的组件，镜像分别由 `server-v*` 与 `admin-v*` tag 触发的发版流水线构建并推送，部署机不做构建。

用户与节点数据都在外置 MySQL 里，日常运维（加人、停用、换落地出口、换席位凭据）走 `/api/admin/**` 接口，**不需要改配置文件、不需要重启服务**。

## 首次部署

> 以下 1～7 步的命令都在仓库的 `deploy/` 目录下执行——先 `cd deploy`。（`mise run up` / `mise run down` 例外：它们是 mise task，任务定义里已带 `dir`，在仓库任意目录执行都可以。）
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

3. **生成密钥并写入 `.env`**：

   ```bash
   cp .env.example .env
   openssl rand -base64 32   # 把输出填进 .env 的 LANE_CRYPTO_KEY
   openssl rand -base64 32   # 再生成一个，填进 .env 的 LANE_AUTH_SESSION_SECRET
   ```

   > ⚠️ `LANE_CRYPTO_KEY` 用来加密席位凭据与节点密码。**丢失或更换 = 库里所有密文永久解不开**，必须重录全部凭据。请与数据库口令分开备份。
   >
   > `LANE_AUTH_SESSION_SECRET` 是自签会话 token 的 HS256 签名密钥（至少 32 字节）。换掉它会让所有已登录会话（含管理端网页与桌面端钥匙串里的）立即失效——这也是一种应急踢下线手段。
   >
   > `.env` 里还有一个 `LOGTO_CLIENT_SECRET`，不是本地生成的，而是**第 5 步**在 Logto 控制台建好传统 Web 应用后从控制台复制过来的，先留空，走到那一步再填。

4. **放置生产配置**：把 `apps/server/src/main/resources/application-prod.yaml.example` 复制到本目录改名 `application-prod.yaml`，填入 Logto issuer 与传统 Web 应用的 App ID、管理端域名（`lane.auth.admin-frontend-url`）。`client-secret` 与会话签名密钥不写在这个文件里，由 compose 从 `.env` 注入。该文件**不含任何凭据**，但仍在 `.gitignore` 中，不入库。

5. **建 Logto 的传统 Web 应用并放置管理端运行时配置**：

   a. 在 Logto 控制台新建一个 **Traditional Web** 类型的应用。登录（无论是管理端网页还是桌面端）现在统一由**服务端**发起并用这一个应用做 authorization code 交换，不再需要像过去那样为桌面端、管理端、API 分别建应用——原来的 Native 应用、SPA 应用与 API Resource 都不再需要，可以在控制台删掉。只填一个地址：

   | 项 | 值 |
   |---|---|
   | Redirect URI | `https://<api 域名>/auth/callback` |

   把 App ID 和 App Secret 记下来：App ID 填进第 4 步的 `application-prod.yaml`（`spring.security.oauth2.client.registration.logto.client-id`）；App Secret 回到第 3 步，填进 `.env` 的 `LOGTO_CLIENT_SECRET`。

   b. 把模板复制成部署机上的运行时配置并填真值：

   ```bash
   cp <仓库>/apps/admin/config.example.json admin-config.json
   ```

   ```json
   {
     "apiBaseUrl": "/api"
   }
   ```

   > 管理端网页现在不再直连 Logto——登录、回调、会话全由服务端承担，管理端只需要知道 API 前缀在哪。`logtoEndpoint`/`logtoAppId`/`apiResource` 这几个字段已不再使用；`config.example.json` 与 `apps/admin/src` 对该配置类型的收窄在计划三落地，本文档先按最终形态说明。
   >
   > 这个文件由 compose 以只读卷挂进 nginx 的站点根目录，**镜像里没有它**——因此同一个镜像可以部署到任何环境，改 API 地址不需要重新构建。它已在 `.gitignore` 中，不入库。
   >
   > ⚠️ **必须在 `mise run up` 之前把这个文件建好**：绑定挂载的宿主侧路径不存在时，Docker 会**自作主张建成一个空目录**，nginx 于是把 `/config.json` 当目录处理、返回 404，页面显示「管理端启动失败」。若已经踩到，先 `mise run down`、`rmdir admin-config.json`、建好真文件再起。

   > ⚠️ **部署约束**：管理端与 API 必须部署在**同一注册域名**下（如 `admin.x.com` 与 `api.x.com` 都属于 `x.com`）。服务端签发的会话 Cookie 是 `SameSite=Lax`，跨注册域名（如 `admin.x.com` 与 `api.y.com`）时浏览器不会带上这个 Cookie，登录态传不过去。

6. **拉起服务**：

   ```bash
   mise run up
   ```

7. **录入初始数据**（首次没有管理员，用「登录一次 + 改库提权」配合完成——**新会话模型下不能再拿 Logto 的 access token 当 Bearer**，服务端只认自签会话 token）：

   a. 浏览器访问 `https://<api 域名>/oauth2/authorization/logto`，用一个已在 Logto 注册的账号登录一次。登录成功即触发服务端唯一的建档入口——库里自动出现一行 `role=MEMBER` 的新用户，浏览器也已经拿到会话 Cookie（`lane_session`）。登录后 302 到管理端地址此时打不开属正常（管理端还没跟进新协议，由计划三接上），不影响建档与 Cookie 已经生效。

   b. 库里把这个账号提为管理员。**首个管理员只能改库产生**——这是设计决定，管理端本就不提供改角色的入口（见下一节「授予或撤销管理员」）：

   ```sql
   UPDATE app_user SET role = 'ADMIN' WHERE email = '<你刚登录用的邮箱>';
   ```

   c. 从浏览器开发者工具（Application → Cookies，找 api 域名下的 `lane_session`）复制它的值——这就是自签会话 token，Bearer 头与 Cookie 两种载体服务端都认。先验证登录态与刚才的提权：

   ```bash
   curl https://<api 域名>/api/me -H "Authorization: Bearer <lane_session 的值>"
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
   curl -X PUT https://<api 域名>/api/admin/nodes/1 \
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
| 启动（服务端 + 管理端） | `mise run up` |
| 停止 | `mise run down` |
| 查看日志（需在 `deploy/` 目录下执行） | `docker compose logs -f server` / `docker compose logs -f admin` |
| 健康检查 | `curl 127.0.0.1:8081/actuator/health` / `curl -I 127.0.0.1:8082/` |

从二期起，下面这些接口日常**不需要手工调**——打开管理端页面点就行。表格保留是为了排查问题时能直接验接口。

管理接口（都需要 ADMIN 账号的 access token）：

| 操作 | 请求 |
|---|---|
| 节点列表 | `GET /api/admin/nodes?role=LAND` |
| 新建节点 | `POST /api/admin/nodes` |
| 改节点（密码留空即不改） | `PUT /api/admin/nodes/{id}` |
| 删节点（被引用会拒绝） | `DELETE /api/admin/nodes/{id}` |
| 用户列表 | `GET /api/admin/users?keyword=&pageNo=1&pageSize=20` |
| 新建用户 | `POST /api/admin/users` |
| 改用户（凭据留空即不改） | `PUT /api/admin/users/{id}` |
| 删用户 | `DELETE /api/admin/users/{id}` |

停用某人（终端下一次心跳即断链）：把其 `status` 改成 `SUSPENDED` 或 `REVOKED`。

## 可调参数

`deploy/.env` 里的覆盖点：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_TAG` | `latest` | 镜像版本。回滚时指定具体版本，如 `SERVER_TAG=0.1.0` |
| `SERVER_PORT` | `8081` | 宿主监听端口 |
| `ADMIN_TAG` | `latest` | 管理端镜像版本。回滚时指定具体版本，如 `ADMIN_TAG=0.1.0` |
| `ADMIN_PORT` | `8082` | 管理端的宿主监听端口 |
| `MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 无（必填） | 外置 MySQL 连接信息 |
| `LANE_CRYPTO_KEY` | 无（必填） | 敏感字段加密密钥，Base64 的 32 字节 |

## 备份

要备份两样东西，**且必须分开存放**：

1. **数据库**（`mysqldump lane`）——里面的凭据是密文。
2. **`LANE_CRYPTO_KEY`**——没有它，数据库备份里的凭据无法解开。

两者存在同一处等于加密白做。

## 对外暴露

两个容器端口**都只绑 `127.0.0.1`**，公网访问不到。对外由宿主上的反代承担，且**管理端与服务端必须同域分路径**——`/` 给管理端，`/api` 给服务端。这样前端不需要 CORS，Logto 的回调也只有一个源。

```nginx
server {
    listen 443 ssl;
    server_name admin.example.com;

    # /api/ 前缀更长，nginx 按最长前缀匹配优先命中它，与两段的书写顺序无关
    location /api/ {
        # 不带路径的 proxy_pass 会原样保留 URI，服务端拿到的仍是 /api/admin/users
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

> 注意：Docker 自己写的 iptables `DOCKER` 链在 ufw 规则之前，`ufw deny <端口>` 拦不住已发布的容器端口。因此「不对外暴露」只能靠绑定地址收口，不能指望防火墙——这就是端口写成三段式 `127.0.0.1:<宿主端口>:<容器端口>` 的原因。

## 发版顺序（桌面端与服务端）

> ⚠️ **登录体系重构一期只改了服务端**：本文档描述的是新协议（服务端自签会话 + Logto 传统 Web 应用），桌面端与管理端要等计划二/三跟进后才能配合这套协议登录。旧的 `GET /api/client-config` 端点已随本次重构下线——若线上还有跑旧协议的桌面端，升级服务端会让它们的登录立即失效，升级前请确认桌面端/管理端已同步跟进，不要单独抢先上线服务端。

服务端上线前确认 `application-prod.yaml` 里已配：

- `spring.security.oauth2.client.registration.logto.client-id`（Logto 传统 Web 应用的 App ID）
- `spring.security.oauth2.client.provider.logto.issuer-uri`（形如 `https://<租户>.logto.app/oidc`）
- `lane.auth.admin-frontend-url`（管理端网页地址，登录成功/失败后的回跳落点）

`client-secret` 与 `lane.auth.session-secret` 不写在这个文件里，由 compose 从 `.env` 的 `LOGTO_CLIENT_SECRET`、`LANE_AUTH_SESSION_SECRET` 注入。
