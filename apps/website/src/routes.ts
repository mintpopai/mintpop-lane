// 全站路径的单一来源：预渲染列表、sitemap 校验、页面内链都从这里派生。
// 只放纯逻辑（无 vue-router 依赖），测试在 node 环境直接跑。
import { GUIDES } from "./content/guides";
import type { Locale } from "./i18n";

export const SITE = "https://lane.mintpop.ai";

/** 指南页路径：zh → /guides/<slug>/，en → /en/guides/<slug>/。带尾斜杠，与 nginx 目录形态、canonical 一致 */
export function guidePath(locale: Locale, slug: string): string {
  return locale === "zh" ? `/guides/${slug}/` : `/en/guides/${slug}/`;
}

/** 预渲染期 route.path 不带尾斜杠（/en、/guides/x），对外 URL 一律补上 */
export function canonicalPath(routePath: string): string {
  return routePath.endsWith("/") ? routePath : `${routePath}/`;
}

/** 交给 vite-ssg 逐条预渲染的路径（无尾斜杠：vite-ssg nested 模式按路径段建目录，/guides/x → guides/x/index.html） */
export const ROUTE_PATHS: string[] = [
  "/",
  "/en",
  ...GUIDES.flatMap((g) => [`/guides/${g.slug}`, `/en/guides/${g.slug}`]),
];
