// 入口：vite-ssg 路由式 SSG——构建期把每条路由预渲染成独立 HTML（正文进 HTML，
// 不执行 JS 的爬虫也可见），客户端水合后仍是完整 Vue 应用。
// 约束：模块顶层与 setup 里执行的代码必须 SSR 安全（构建期没有 window/document/navigator）。
//
// 字体自托管（fontsource）：不引外链字体域名，保证含中国大陆在内的全部目标地区可达。
// 中文字形由系统字体栈兜底（PingFang / 微软雅黑），只为拉丁字形与数字引这三族。
//
// 三族的分工由 MintPop 品牌规范定死：展示字 Fredoka（品牌二选一，且与桌面端 --font-brand 同源）、
// 正文 Space Grotesk（INVARIANT，不可换）、等宽 JetBrains Mono（与桌面端终端同一种字）。
import "@fontsource/fredoka/500.css";
import "@fontsource/fredoka/600.css";
import "@fontsource/space-grotesk/400.css";
import "@fontsource/space-grotesk/500.css";
import "@fontsource/space-grotesk/600.css";
import "@fontsource/space-grotesk/700.css";
import "@fontsource/jetbrains-mono/400.css";
import "@fontsource/jetbrains-mono/500.css";
import { ViteSSG } from "vite-ssg";
import App from "./App.vue";
import GuidePage from "./pages/GuidePage.vue";
import HomePage from "./pages/HomePage.vue";
import { localeFromPath, switchLocalePath } from "./i18n";
import { ROUTE_PATHS, canonicalPath } from "./routes";
import "./styles.css";

// / 与 /en/ 是同一页的两个语言版本（locale 由路由派生，见 i18n.ts）；
// 指南页两条动态路由，具体 slug 由 includedRoutes 展开、逐条预渲染。
// 以后加指南：content/guides/ 下建文件 + 注册表加一行，这里不用动。
const routes = [
  { path: "/", component: HomePage },
  { path: "/en", component: HomePage },
  { path: "/guides/:slug", component: GuidePage },
  { path: "/en/guides/:slug", component: GuidePage },
];

export const createApp = ViteSSG(App, {
  routes,
  // 站内跳转（首页 → 指南、FAQ 的延伸阅读）默认会停在原滚动位置，落到长文页底部：
  // - 浏览器前进/后退恢复原位；
  // - 带锚点滚到锚点；
  // - 同一页面换语言留在原处（顶栏的中 / EN，见 TheHeader.vue 的 B2.2）；
  // - 其余一律回到页顶，且用 instant：styles.css 给 html 设了 scroll-behavior: smooth，
  //   不指定就会从旧页底部「平滑滚」到新页顶部，像是页面自己在动。
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition;
    if (to.hash) return { el: to.hash };
    if (switchLocalePath(from.path, localeFromPath(to.path)) === canonicalPath(to.path))
      return false;
    return { top: 0, behavior: "instant" };
  },
});

// vite-ssg 只会自动预渲染静态路径；动态路由要在这里展开成具体路径（与 sitemap 同源，见 routes.ts）
export function includedRoutes(): string[] {
  return ROUTE_PATHS;
}
