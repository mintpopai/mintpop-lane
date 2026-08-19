import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import "element-plus/dist/index.css";
import "./styles/global.css";
import App from "./App.vue";
import { loadRuntimeConfig } from "./config/runtime";
import { createAppRouter } from "./router";

/** 配置读不到就没法建 Logto 客户端，直接给一句能照着排查的话，别让页面白屏 */
function 显示启动失败(message: string): void {
  const box = document.createElement("main");
  box.style.padding = "24px";
  box.style.color = "#c45656";
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
  app.use(ElementPlus, { locale: zhCn });
  app.use(createAppRouter());
  app.mount("#app");
}

void bootstrap();
