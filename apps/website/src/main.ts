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
import HomePage from "./pages/HomePage.vue";
import "./styles.css";

// / 与 /en/ 是同一页的两个语言版本（locale 由路由派生，见 i18n.ts），
// 各自预渲染成一份 HTML、分别被搜索引擎收录。
// 以后加页面：pages/ 下建组件 + 这里加路由，构建即多出一份预渲染 HTML。
const routes = [
  { path: "/", component: HomePage },
  { path: "/en", component: HomePage },
];

export const createApp = ViteSSG(App, { routes });
