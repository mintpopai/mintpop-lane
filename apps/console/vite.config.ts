/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    // 本地端口固定 6203（本项目占 6200 段：server 6200、admin 6201、website 6202、console 6203），
    // strictPort 保证被占用时直接报错退出、绝不自己漂到别的端口——Logto 登记的本地回调写死了它
    port: 6203,
    strictPort: true,
    proxy: {
      // 本地开发把接口与登录握手都转给本机服务端；线上由容器内 nginx 转发同样三段，
      // 全环境同域：Cookie 天然携带，无 CORS。本地需在 Logto 应用追加回调 http://localhost:6203/auth/callback
      "/api": { target: "http://127.0.0.1:6200" },
      "/auth": { target: "http://127.0.0.1:6200" },
      "/oauth2": { target: "http://127.0.0.1:6200" },
    },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.spec.ts"],
  },
});
