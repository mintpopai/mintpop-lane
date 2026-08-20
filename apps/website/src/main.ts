// 字体自托管（fontsource）：不引外链字体域名，保证含中国大陆在内的全部目标地区可达。
// 中文字形由系统字体栈兜底（PingFang / 微软雅黑），只为拉丁字形与数字引这三族。
import "@fontsource/fredoka/500.css";
import "@fontsource/fredoka/600.css";
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import { createApp } from "vue";
import App from "./App.vue";
import "./styles.css";

createApp(App).mount("#app");
