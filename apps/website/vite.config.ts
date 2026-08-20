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
      // dev 下把同源端点 /api/gh/releases 转发到桌面端仓库的 releases 列表，
      // 与 prod 的 nginx 反代对齐（前端代码统一打这一个同源端点）。本地直连、不带缓存，限流无所谓。
      "/api/gh/releases": {
        target: "https://api.github.com",
        changeOrigin: true,
        headers: {
          // GitHub API 强制要求 UA，缺了会 403
          "User-Agent": "mintpop-lane-website",
          Accept: "application/vnd.github+json",
        },
        // per_page=10 与 prod nginx 对齐：收窄响应体（版本再多也固定上限）
        rewrite: () => "/repos/mintpopai/mintpop-lane-desktop/releases?per_page=10",
      },
    },
  },
  test: {
    // 下载逻辑是纯函数，node 环境即可，不依赖 jsdom
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
