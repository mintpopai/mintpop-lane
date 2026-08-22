# ---- 构建阶段：干净 slim 底座 + curl 装 mise（官方写法）----
FROM debian:13-slim AS build

# mise 自身版本在此钉死——自举工具无法由 mise.toml 管，这是 Dockerfile 里唯一允许的版本钉死
ENV MISE_VERSION=v2026.6.0
ENV MISE_DATA_DIR=/mise MISE_CONFIG_DIR=/mise MISE_CACHE_DIR=/mise/cache \
    MISE_INSTALL_PATH=/usr/local/bin/mise PATH=/mise/shims:$PATH
# 关掉 mise run 跑 task 前的「自动装全部 [tools]」，否则会把 rust/node 等无关工具链一并拉下来
ENV MISE_TASK_RUN_AUTO_INSTALL=false

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl https://mise.run | sh

WORKDIR /app

# 只装本镜像需要的工具，不要 mise install 全装
COPY mise.toml ./
RUN mise trust && mise install java maven

# 依赖清单与源码分两次 COPY：改源码不会让依赖下载层失效
COPY apps/server/pom.xml ./apps/server/
RUN mise run install-server

COPY apps/server/src ./apps/server/src
# 构建命令走 mise task，与本地/CI 单一来源，不在 Dockerfile 里重抄 mvn 命令
RUN mise run build-server

# ---- 运行阶段：JRE 底座只装 jar；curl 供 compose healthcheck ----
# 运行期不进 mise：mise exec 会在容器启动时自动安装 mise.toml [tools] 里没装的
# node/pnpm/maven（node 在 slim 底座缺 libatomic 直接装不上，容器启动即崩）。
# JRE 大版本与根 mise.toml 的 java 保持一致，升级 java 时两处同步改
FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# 部署时把外置 application.yml 挂到 /app/config/，Spring 启动自动读取（凭据不进镜像）
COPY --from=build /app/apps/server/target/lane-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
