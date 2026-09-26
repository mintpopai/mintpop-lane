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
    // 本地端口固定 6202（本项目占 6200 段：server 6200、admin 6201、website 6202、console 6203），
    // strictPort 保证被占用时直接报错退出、绝不自己漂到别的端口
    port: 6202,
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
  // 本地预览构建产物（mise run preview-website）同样钉死端口，与 dev 错开可同时起
  preview: {
    port: 6205,
    strictPort: true,
  },
  test: {
    // 组件测试要挂 DOM，与管理端统一用 jsdom（纯逻辑那几个 spec 在 jsdom 下照样跑）
    environment: "jsdom",
    include: ["src/**/*.spec.ts"],
  },
});
