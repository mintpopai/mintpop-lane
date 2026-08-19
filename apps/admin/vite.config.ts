/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    // 端口固定：Logto 应用里注册的回调地址是写死的 http://localhost:5173/callback，
    // 端口一漂移登录就回不来了
    port: 5173,
    strictPort: true,
    proxy: {
      // 本地开发把 /api 转给本机服务端；线上是同域分路径，既不需要代理也不需要 CORS
      "/api": { target: "http://127.0.0.1:8080" },
    },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.spec.ts"],
  },
});
