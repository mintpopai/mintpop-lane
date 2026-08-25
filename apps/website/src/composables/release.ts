// 下载逻辑纯函数：按 UA 猜系统、从分发清单里取出各平台下载直链与体积。
// 不做副作用（fetch 在 useRelease 里），便于单测。
//
// 数据源为何是「同源反代的分发清单」而非 GitHub releases 列表：
//   ① 桌面端仓库已转私有，GitHub Release 的资产对匿名访客一律 404，不能再当下载源。
//      制品改发 Cloudflare R2（dl.mintpop.ai），发布流水线在正式版发版时写一份 downloads.json。
//   ② 仍走官网自己的 nginx 同源反代（/api/dist/downloads）而非让浏览器直连 dl.mintpop.ai：
//      同源就不必给 R2 配 CORS（跨域 fetch 没有 CORS 头会被浏览器丢弃），
//      且反代的 proxy_cache_use_stale 能在上游抽风时继续供旧清单。dev 下由 vite proxy 转发到同一端点。
//   ③ 清单只在正式版发版时被覆盖（预发布只写版本化目录、不动指针），故这里不再需要过滤
//      prerelease/draft；平台键也由发布侧显式给出，不再靠文件名后缀猜哪个资产属于哪个平台。
export type OS = "MAC" | "WINDOWS" | "OTHER";
export type MatchKey = "MAC_ARM" | "WINDOWS";

/** 同源反代端点（prod→nginx，dev→vite proxy），上游是 dl.mintpop.ai 上的 downloads.json */
export const DOWNLOADS_API = "/api/dist/downloads";

/** 分发清单里单个平台的条目 */
export interface ManifestPlatform {
  url: string;
  size: number;
}

/** 发布流水线写出的分发清单 */
export interface DownloadsManifest {
  version: string;
  pubDate: string;
  platforms: Record<string, ManifestPlatform>;
}

// 清单平台键 → 页面匹配键。平台键由 Tauri updater 约定，与桌面端仓库逐字一致，改动要两端同步。
const PLATFORM_KEYS: Record<MatchKey, string> = {
  MAC_ARM: "darwin-aarch64",
  WINDOWS: "windows-x86_64",
};

export type Matched = Record<MatchKey, ManifestPlatform | null>;
export interface DesktopRelease {
  version: string; // 裸版本号 x.y.z（清单里本就不带 v 前缀）
  platforms: Matched;
}

// 清单来自网络，字段形状不做假设：任一必需字段缺失或类型不对就整体判为拿不到，
// 由调用方兜底成「下载按钮不可用」，而不是让 undefined 漏进 href。
export function parseDownloads(manifest: unknown): DesktopRelease | null {
  if (typeof manifest !== "object" || manifest === null) return null;
  const m = manifest as Partial<DownloadsManifest>;
  if (typeof m.version !== "string" || m.version === "") return null;
  if (typeof m.platforms !== "object" || m.platforms === null) return null;

  const platforms = m.platforms as Record<string, unknown>;
  const pick = (key: string): ManifestPlatform | null => {
    const entry = platforms[key];
    if (typeof entry !== "object" || entry === null) return null;
    const { url, size } = entry as Partial<ManifestPlatform>;
    if (typeof url !== "string" || url === "") return null;
    return { url, size: typeof size === "number" ? size : 0 };
  };

  return {
    version: m.version,
    platforms: {
      MAC_ARM: pick(PLATFORM_KEYS.MAC_ARM),
      WINDOWS: pick(PLATFORM_KEYS.WINDOWS),
    },
  };
}

/** 字节数转成给人看的近似体积；拿不到有效体积时给 null（调用方不渲染这一段） */
export function formatSize(bytes: number): string | null {
  if (!Number.isFinite(bytes) || bytes <= 0) return null;
  return `约 ${Math.round(bytes / 1024 / 1024)} MB`;
}

// 移动端必须先排除再判桌面系统：iPhone/iPad 的 UA 都含 "like Mac OS X"，直接判会误发 .dmg。
// iPadOS 13+ 桌面模式的 UA 与真 Mac 逐字相同，UA 无从区分，只能靠触点数（Mac 无触屏，maxTouchPoints 为 0）
export function detectOS(ua: string, maxTouchPoints = 0): OS {
  if (/iPhone|iPad|iPod|Android/i.test(ua)) return "OTHER";
  if (/Windows/i.test(ua)) return "WINDOWS";
  if (/Mac OS X|Macintosh/i.test(ua)) return maxTouchPoints > 1 ? "OTHER" : "MAC";
  return "OTHER";
}

// Hero 主按钮：按系统挑默认匹配键（mac 默认 Apple 芯片）
export function primaryMatchKey(os: OS): MatchKey | null {
  if (os === "MAC") return "MAC_ARM";
  if (os === "WINDOWS") return "WINDOWS";
  return null;
}
