import { createApp } from "vue";
import { createPinia } from "pinia";
// 字体自托管（Fontsource），禁止外链 Google Fonts。拉丁字形打进产物，
// 中文回落系统字体栈（PingFang / 微软雅黑），一个字节的外部请求都不产生
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/fredoka/600.css";
// 等宽只用于「系统生成的事实」（邮箱 / ID / 时间戳 / IP），排版即信息，见 layout.css
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import "./styles/base.css";
import "./styles/layout.css";
import App from "./App.vue";
import { createAppRouter } from "./router";

const app = createApp(App);
app.use(createPinia());
app.use(createAppRouter());
app.mount("#app");
