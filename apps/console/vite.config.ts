/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    // 端口固定：Logto 登记的本地回调地址写死了这个端口（5173 是管理端、5174 是官网）
    port: 5175,
    strictPort: true,
    proxy: {
      // 本地开发把接口与登录握手都转给本机服务端；线上由容器内 nginx 转发同样三段，
      // 全环境同域：Cookie 天然携带，无 CORS。本地需在 Logto 应用追加回调 http://localhost:5175/auth/callback
      "/api": { target: "http://127.0.0.1:8080" },
      "/auth": { target: "http://127.0.0.1:8080" },
      "/oauth2": { target: "http://127.0.0.1:8080" },
    },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.spec.ts"],
  },
});
