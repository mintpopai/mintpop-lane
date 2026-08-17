# 服务端部署

拉取 GHCR 上已发布的镜像运行。镜像由 `server-v*` tag 触发的发版流水线构建并推送，部署机不做构建。

## 首次部署

1. **登录 GHCR**（镜像为私有包时必须）：

   ```bash
   docker login ghcr.io -u <你的 GitHub 用户名>
   # 密码用一个具备 read:packages 权限的 PAT
   ```

2. **放置生产配置**：把 `apps/server/src/main/resources/application-prod.yaml.example` 复制到本目录并改名为 `application-prod.yaml`，填入真实的 Logto issuer/audience 与员工绑定表。

   该文件含节点凭据与 Claude 席位凭据，已在 `.gitignore` 中排除，**严禁入库**。建议权限设为 `600`。

3. **拉起服务**：

   ```bash
   mise run up
   ```

## 日常操作

| 操作 | 命令 |
|---|---|
| 启动 | `mise run up` |
| 停止 | `mise run down` |
| 查看日志 | `docker compose logs -f server` |
| 健康检查 | `curl 127.0.0.1:8081/actuator/health` |

## 可调参数

两个环境变量覆盖点，不必改 compose 文件：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SERVER_TAG` | `latest` | 镜像版本。回滚时指定具体版本，如 `SERVER_TAG=0.1.0 mise run up` |
| `SERVER_PORT` | `8081` | 宿主监听端口 |

## 对外暴露

容器端口**只绑 `127.0.0.1`**，公网访问不到。对外服务由宿主上的反代承担：nginx / Caddy 反代到 `127.0.0.1:8081`，或用 cloudflared 从本机连出去。

> 注意：Docker 自己写的 iptables `DOCKER` 链在 ufw 规则之前，`ufw deny <端口>` 拦不住已发布的容器端口。因此「不对外暴露」只能靠绑定地址收口，不能指望防火墙——这就是端口写成三段式 `127.0.0.1:<宿主端口>:<容器端口>` 的原因。
