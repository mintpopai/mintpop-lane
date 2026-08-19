import js from "@eslint/js";
import globals from "globals";
import pluginVue from "eslint-plugin-vue";
import { defineConfigWithVueTs, vueTsConfigs } from "@vue/eslint-config-typescript";

export default defineConfigWithVueTs(
  { name: "忽略构建产物", ignores: ["dist/**", "node_modules/**"] },
  {
    name: "浏览器全局变量",
    files: ["**/*.{ts,vue}"],
    languageOptions: { globals: { ...globals.browser } },
  },
  js.configs.recommended,
  pluginVue.configs["flat/essential"],
  vueTsConfigs.recommended,
);
