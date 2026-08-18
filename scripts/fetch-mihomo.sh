#!/usr/bin/env bash
# 下载 mihomo 内核并按 Tauri sidecar 命名规则放好。
# 打包前执行一次即可；产物不入库，由 CI 每次重新下载。
set -euo pipefail

MIHOMO_VERSION="${MIHOMO_VERSION:-v1.19.14}"
OUT_DIR="apps/desktop/src-tauri/binaries"
mkdir -p "$OUT_DIR"

# 目标三元组必须与 rustc 一致，Tauri 据此匹配 sidecar
TRIPLE="$(rustc -vV | awk '/host:/ {print $2}')"

case "$TRIPLE" in
  aarch64-apple-darwin)   ASSET="mihomo-darwin-arm64-${MIHOMO_VERSION}.gz";  EXT="" ;;
  x86_64-pc-windows-msvc) ASSET="mihomo-windows-amd64-${MIHOMO_VERSION}.zip"; EXT=".exe" ;;
  *) echo "不支持的目标平台：${TRIPLE}" >&2; exit 1 ;;
esac

DEST="${OUT_DIR}/mihomo-${TRIPLE}${EXT}"
# 已就位则跳过，使 install 可重复执行；MIHOMO_FORCE=1 强制重新下载
if [ -x "$DEST" ] && [ "${MIHOMO_FORCE:-}" != "1" ]; then
  echo "已存在，跳过下载：${DEST}"
  exit 0
fi

URL="https://github.com/MetaCubeX/mihomo/releases/download/${MIHOMO_VERSION}/${ASSET}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "下载 ${URL}"
curl -fsSL "$URL" -o "${TMP}/${ASSET}"

case "$ASSET" in
  *.gz)
    gunzip -c "${TMP}/${ASSET}" > "$DEST"
    ;;
  *.zip)
    unzip -q -o "${TMP}/${ASSET}" -d "$TMP"
    mv "$TMP"/mihomo*.exe "$DEST"
    ;;
esac

chmod +x "$DEST"
echo "已就位：${DEST}"
