/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    // 端口固定：下方登录回调地址写死了这个端口，端口一漂移登录就回不来了
    port: 5173,
    strictPort: true,
    proxy: {
      // 本地开发把接口与登录握手都转给本机服务端；线上是同域分路径（nginx 转发同样三段），
      // 全环境同域：Cookie 天然携带，无 CORS。
      // 登录回调 {baseUrl}/auth/callback 按 Host 头解析，Vite 代理不改写 Host，
      // 故本地需在 Logto 应用追加回调地址 http://localhost:5173/auth/callback
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
