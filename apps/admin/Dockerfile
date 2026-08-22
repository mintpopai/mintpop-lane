# ---- 构建阶段：干净 slim 底座 + curl 装 mise（官方写法）----
FROM debian:13-slim AS build

# mise 自身版本在此钉死——自举工具无法由 mise.toml 管，这是 Dockerfile 里唯一允许的版本钉死
ENV MISE_VERSION=v2026.6.0
ENV MISE_DATA_DIR=/mise MISE_CONFIG_DIR=/mise MISE_CACHE_DIR=/mise/cache \
    MISE_INSTALL_PATH=/usr/local/bin/mise PATH=/mise/shims:$PATH
# 关掉 mise run 跑 task 前的「自动装全部 [tools]」，否则会把 java/rust 等无关工具链一并拉下来
ENV MISE_TASK_RUN_AUTO_INSTALL=false

# libatomic1：Node 的官方预编译 arm64 二进制运行时链接 libatomic.so.1，而 debian slim 不带它，
# 缺了会让 mise install 在探测 node -v 时以 exit 127 失败（Apple Silicon 上本地构建必踩）。
# amd64 的 node 二进制不需要它，但装上只有几十 KB，不值得为架构分支
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl git ca-certificates libatomic1 \
    && rm -rf /var/lib/apt/lists/* \
    && curl https://mise.run | sh

WORKDIR /app

# 只装本镜像需要的工具，不要 mise install 全装
COPY mise.toml ./
RUN mise trust && mise install node pnpm

# 依赖清单与源码分两次 COPY：改源码不会让依赖安装层失效。
# pnpm 是工作区安装，所以工作区清单与 lockfile 都要进来；--filter 保证只装管理端这一个包，
# 桌面端的 package.json 不在上下文里也不影响（pnpm 只装被选中的项目）
COPY pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/admin/package.json ./apps/admin/
RUN mise run install-admin --frozen

COPY apps/admin/ ./apps/admin/
# 构建命令走 mise task，与本地/CI 单一来源，不在 Dockerfile 里重抄 pnpm/vite 命令
RUN mise run build-admin

# ---- 运行阶段：只有 nginx 与静态产物，构建工具链不进最终镜像 ----
FROM nginx:1.30-alpine AS runtime

COPY apps/admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/apps/admin/dist /usr/share/nginx/html

EXPOSE 80
