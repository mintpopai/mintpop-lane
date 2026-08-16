import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// Tauri 期望前端开发服务器固定在 1420 端口，且不要清屏以免遮挡 Rust 侧日志
export default defineConfig({
  plugins: [vue()],
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
  },
});
