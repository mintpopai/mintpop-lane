import js from "@eslint/js";
import globals from "globals";
import pluginVue from "eslint-plugin-vue";
import { defineConfigWithVueTs, vueTsConfigs } from "@vue/eslint-config-typescript";
// 必须放在所有规则集之后：关掉与 prettier 冲突的格式类规则，
// 「怎么排版」全交给 prettier --check（见 lint task），eslint 只管代码质量
import skipFormatting from "@vue/eslint-config-prettier/skip-formatting";

export default defineConfigWithVueTs(
  { name: "忽略构建产物", ignores: ["dist/**", "node_modules/**"] },
  {
    name: "浏览器全局变量",
    files: ["**/*.{ts,vue}"],
    languageOptions: { globals: { ...globals.browser } },
  },
  {
    // 构建配置与单元测试跑在 node 里，不给 node 全局变量会把 process 这类判成未定义
    name: "Node 全局变量",
    files: ["*.{js,ts}", "**/*.spec.ts"],
    languageOptions: { globals: { ...globals.node } },
  },
  js.configs.recommended,
  pluginVue.configs["flat/recommended"],
  vueTsConfigs.recommended,
  {
    // 测试夹具与探针组件天然要在一个文件里定义好几个小组件，
    // 而这条规则针对的是生产用的 SFC，在测试里只会制造噪声
    name: "测试文件放宽",
    files: ["**/*.spec.ts", "**/testing.ts"],
    rules: { "vue/one-component-per-file": "off" },
  },
  skipFormatting,
);
