/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
// 类型层导入：加载 vite-ssg 对 vite UserConfig 的模块扩充，让下方 ssgOptions 有类型
import type {} from "vite-ssg";

export default defineConfig({
  plugins: [vue()],
  // nested：路由 /en 输出 dist/en/index.html（目录 + index），nginx 的 try_files $uri/ 直接命中；
  // 默认 flat 会输出 en.html，与「URL 不带 .html」的路径对不上
  ssgOptions: {
    dirStyle: "nested",
  },
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
