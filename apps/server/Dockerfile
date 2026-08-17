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

COPY apps/server/ ./apps/server/
# 构建命令走 mise task，与本地/CI 单一来源，不在 Dockerfile 里重抄 mvn 命令
RUN mise run build-server

# ---- 运行阶段：同样用 mise 提供 JRE，保持版本单一来源 ----
FROM debian:13-slim AS runtime

ENV MISE_VERSION=v2026.6.0
ENV MISE_DATA_DIR=/mise MISE_CONFIG_DIR=/mise MISE_CACHE_DIR=/mise/cache \
    MISE_INSTALL_PATH=/usr/local/bin/mise PATH=/mise/shims:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates wget \
    && rm -rf /var/lib/apt/lists/* \
    && curl https://mise.run | sh

WORKDIR /app
# 运行期只需要 java，不装 maven
COPY mise.toml ./
RUN mise trust && mise install java

COPY --from=build /app/apps/server/target/mintpop-server-*.jar ./server.jar

EXPOSE 8080
# 配置由 compose 以只读卷挂到 /app/config，含凭据的配置文件不进镜像
CMD ["mise", "exec", "--", "java", "-jar", "server.jar", "--spring.config.additional-location=file:/app/config/"]
