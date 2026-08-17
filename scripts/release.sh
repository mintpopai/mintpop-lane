#!/usr/bin/env bash
# 发版：校验 → 打 tag → 推送。由 tag 触发对应组件的发版流水线。
# 用法见根 mise.toml 的 release-<组件> task。
set -euo pipefail

COMPONENT="$1"
ARG1="${2:-}"
ARG2="${3:-}"

# 单个参数时按形状消歧：像版本号就当版本号，否则当更新说明
VERSION=""
NOTES=""
if [ -n "$ARG2" ]; then
  VERSION="$ARG1"
  NOTES="$ARG2"
elif [ -n "$ARG1" ]; then
  if printf '%s' "$ARG1" | grep -qE '^v?[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$'; then
    VERSION="$ARG1"
  else
    NOTES="$ARG1"
  fi
fi

# 这一次 fetch 同时供「自动算版本号」与「main 落后检查」复用
git fetch origin main --tags

if [ -z "$VERSION" ]; then
  # 用 git 内置版本序取最新稳定版再 patch+1。
  # 禁止 sort -V：macOS 自带的 BSD sort 不支持它，会静默出错。
  latest="$(git tag -l "${COMPONENT}-v*" --sort=-v:refname \
    | grep -E "^${COMPONENT}-v[0-9]+\.[0-9]+\.[0-9]+$" | head -1 || true)"
  if [ -z "$latest" ]; then
    VERSION="v0.1.0"
  else
    base="${latest#"${COMPONENT}"-v}"
    major="${base%%.*}"
    rest="${base#*.}"
    minor="${rest%%.*}"
    patch="${rest#*.}"
    VERSION="v${major}.${minor}.$((patch + 1))"
  fi
fi

# 允许传不带 v 前缀的版本号
case "$VERSION" in
  v*) ;;
  *) VERSION="v${VERSION}" ;;
esac
TAG="${COMPONENT}-${VERSION}"

# 任一校验不过即中止，绝不打 tag
printf '%s' "$VERSION" | grep -qE '^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$' \
  || { echo "版本号格式非法：${VERSION}" >&2; exit 1; }
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] \
  || { echo "只能在 main 分支发版" >&2; exit 1; }
[ -z "$(git status --porcelain)" ] \
  || { echo "工作区不干净，先提交或暂存改动" >&2; exit 1; }
if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  echo "tag 已存在：${TAG}" >&2
  exit 1
fi
[ -z "$(git rev-list HEAD..origin/main)" ] \
  || { echo "本地 main 落后于 origin/main，先 pull" >&2; exit 1; }

if [ -n "$NOTES" ]; then
  # annotated tag：注释会被置顶到 Release 正文
  git tag -a "$TAG" -m "$NOTES"
else
  git tag "$TAG"
fi

# 先推 main 再推 tag，由 tag 触发发版 workflow
git push origin main && git push origin "$TAG"
echo "已发版：${TAG}"
