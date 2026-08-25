/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    // 5173 留给管理端，官网固定 5174，两个前端可同时起
    port: 5174,
    strictPort: true,
    proxy: {
      // dev 下把同源端点 /api/dist/downloads 转发到 R2 上的分发清单，
      // 与 prod 的 nginx 反代对齐（前端代码统一打这一个同源端点）。本地直连、不带缓存。
      "/api/dist/downloads": {
        target: "https://dl.mintpop.ai",
        changeOrigin: true,
        rewrite: () => "/lane/downloads.json",
      },
    },
  },
  test: {
    // 下载逻辑是纯函数，node 环境即可，不依赖 jsdom
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
