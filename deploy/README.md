# 服务端部署

拉取 GHCR 上已发布的镜像运行。镜像由 `server-v*` tag 触发的发版流水线构建并推送，部署机不做构建。

用户与节点数据都在外置 MySQL 里，日常运维（加人、停用、换落地出口、换席位凭据）走 `/api/admin/**` 接口，**不需要改配置文件、不需要重启服务**。

## 首次部署

> 以下 1～6 步的命令都在仓库的 `deploy/` 目录下执行——先 `cd deploy`。（`mise run up` / `mise run down` 例外：它们是 mise task，任务定义里已带 `dir`，在仓库任意目录执行都可以。）
>
> **前置条件**：外置 MySQL 需为 **8.0 及以上**（本项目开发与测试用的是 8.4）。建库语句用了 `utf8mb4_0900_ai_ci` 排序规则，表结构里也有 JSON 列，这两者都要求 8.0+；5.x 会在建库这一步直接报错。

1. **登录 GHCR**（镜像为私有包时必须）：

   ```bash
   docker login ghcr.io -u <你的 GitHub 用户名>
   # 密码用一个具备 read:packages 权限的 PAT
   ```

2. **在外置 MySQL 上建库**（表由 Flyway 在服务启动时自动创建）：

   ```sql
   CREATE DATABASE mintpop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'mintpop'@'%' IDENTIFIED BY '<口令>';
   GRANT ALL PRIVILEGES ON mintpop.* TO 'mintpop'@'%';
   ```

3. **生成加密密钥并写入 `.env`**：

   ```bash
   cp .env.example .env
   openssl rand -base64 32   # 把输出填进 .env 的 MINTPOP_CRYPTO_KEY
   ```

   > ⚠️ 这个密钥用来加密席位凭据与节点密码。**丢失或更换 = 库里所有密文永久解不开**，必须重录全部凭据。请与数据库口令分开备份。

4. **放置生产配置**：把 `apps/server/src/main/resources/application-prod.yaml.example` 复制到本目录改名 `application-prod.yaml`，填入真实的 Logto issuer 与 audience。该文件现在只有 OIDC 配置与链路有效期，**不含任何凭据**，但仍在 `.gitignore` 中，不入库。

5. **拉起服务**：

   ```bash
   mise run up
   ```

6. **录入初始数据**（首次没有管理员，用 SQL 与接口配合完成）：

   a. 先用一个已在 Logto 注册的账号拿 access token，向 `/api/link/config` 打一次——会得到 `210003 该账号未开通终端使用权限`，说明服务通了。

   b. 直接用 SQL 插入第一个节点与第一个用户（就是你自己），并把角色设为 ADMIN：

   ```sql
   INSERT INTO proxy_node (name, role, protocol, server_addr, port)
   VALUES ('FRONT-1', 'FRONT', 'TROJAN', 'us.example.com', 443);

   INSERT INTO app_user (subject, name, role, front_node_id)
   VALUES ('<你的 Logto user id>', '<你的姓名>', 'ADMIN', 1);
   ```

   > 节点的密码字段 `secret_cipher` 是密文，**不要手写**——插入时先留空，随后用管理接口补齐，由服务端加密写入。

   c. 用刚插入的 ADMIN 账号拿 access token，调用管理接口补齐节点密码。**`PUT /api/admin/nodes/{id}` 是整体覆盖式更新，不是局部补丁**——`name`/`role`/`protocol`/`serverAddr`/`port`/`status` 都是必填校验字段，必须连同 `secret` 一起原样提交，只传 `secret` 会被参数校验挡回来（`110001`）：

   ```bash
   curl -X PUT https://your-domain/api/admin/nodes/1 \
     -H "Authorization: Bearer <access_token>" \
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

   d. 之后的一切（加落地节点、加用户、分配落地出口、录席位凭据）都走管理接口。

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
| 启动 | `mise run up` |
| 停止 | `mise run down` |
| 查看日志（需在 `deploy/` 目录下执行） | `docker compose logs -f server` |
| 健康检查 | `curl 127.0.0.1:8081/actuator/health` |

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
| `MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 无（必填） | 外置 MySQL 连接信息 |
| `MINTPOP_CRYPTO_KEY` | 无（必填） | 敏感字段加密密钥，Base64 的 32 字节 |

## 备份

要备份两样东西，**且必须分开存放**：

1. **数据库**（`mysqldump mintpop`）——里面的凭据是密文。
2. **`MINTPOP_CRYPTO_KEY`**——没有它，数据库备份里的凭据无法解开。

两者存在同一处等于加密白做。

## 对外暴露

容器端口**只绑 `127.0.0.1`**，公网访问不到。对外服务由宿主上的反代承担：nginx / Caddy 反代到 `127.0.0.1:8081`，或用 cloudflared 从本机连出去。

> 注意：Docker 自己写的 iptables `DOCKER` 链在 ufw 规则之前，`ufw deny <端口>` 拦不住已发布的容器端口。因此「不对外暴露」只能靠绑定地址收口，不能指望防火墙——这就是端口写成三段式 `127.0.0.1:<宿主端口>:<容器端口>` 的原因。
