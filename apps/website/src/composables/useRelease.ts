import { computed, onMounted, ref } from "vue";
import {
  detectOS,
  DOWNLOADS_API,
  parseDownloads,
  primaryMatchKey,
  type DesktopRelease,
  type ManifestPlatform,
  type MatchKey,
  type OS,
} from "./release";

// 全站共享一次拉取结果（首屏与下载区复用，避免重复请求）
//
// release / started / os 是模块级可变状态，目前无害：三者只在 onMounted 里被写，
// 而 SSR/vite-ssg 预渲染不跑 onMounted，构建期不会触碰它们。但本模块现在运行在
// vite-ssg 的并发预渲染进程里（i18n.ts 的 locale 之所以做成 provide/inject 而非模块单例，
// 就是为了躲开这类跨路由状态污染）——哪天有人为了 SEO 把清单拉取挪进 setup 顶层执行，
// 这里就会立刻变成 / 与 /en/ 两条预渲染路由互相污染下载数据。改动前请留意这条约束，
// 不要在此基础上把模块级状态的写入点从 onMounted 挪出去。
const release = ref<DesktopRelease | null>(null);
let started = false;

// 首帧固定 "OTHER"（出「前往下载」兜底文案）；真实系统在 onMounted 后按 navigator.userAgent 识别
const os = ref<OS>("OTHER");

async function load() {
  if (started) return;
  started = true;
  try {
    // 同源反代（prod nginx / dev vite proxy）→ dl.mintpop.ai 上的分发清单
    const res = await fetch(DOWNLOADS_API, { headers: { Accept: "application/json" } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    release.value = parseDownloads(await res.json());
  } catch {
    // 失败：不显示版本号、下载按钮转不可用态。页面其余部分不受影响。
    // 不再兜底到 GitHub Releases 页——仓库已私有，那个页面对访客同样是 404。
    release.value = null;
  }
}

export function useRelease() {
  onMounted(() => {
    os.value = detectOS(navigator.userAgent, navigator.maxTouchPoints);
    load();
  });
  const version = computed(() => release.value?.version ?? null);

  /** 拿到清单则给该平台的直链与体积；否则 null（调用方渲染成不可用态） */
  function platformFor(key: MatchKey): ManifestPlatform | null {
    return release.value?.platforms[key] ?? null;
  }
  const primaryKey = computed(() => primaryMatchKey(os.value));

  return { os, version, platformFor, primaryKey };
}
