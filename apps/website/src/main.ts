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
import { createApp } from "vue";
import App from "./App.vue";
import "./styles.css";

createApp(App).mount("#app");
