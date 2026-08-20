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
import { loadRuntimeConfig } from "./config/runtime";
import { createAppRouter } from "./router";

/** 配置读不到就没法确定接口前缀，直接给一句能照着排查的话，别让页面白屏 */
function 显示启动失败(message: string): void {
  const box = document.createElement("main");
  box.style.padding = "24px";
  box.style.color = "#b3341f";
  box.textContent = `管理端启动失败：${message}`;
  document.body.replaceChildren(box);
}

async function bootstrap(): Promise<void> {
  try {
    await loadRuntimeConfig();
  } catch (error) {
    显示启动失败((error as Error).message);
    return;
  }

  const app = createApp(App);
  app.use(createPinia());
  app.use(createAppRouter());
  app.mount("#app");
}

void bootstrap();
