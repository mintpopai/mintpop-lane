// 语言的单一来源是 URL：/ 是中文，/en/ 是英文。
// 这里只放不依赖 vue-router 的纯逻辑；provide/inject 那部分见本文件后半（Task 4 补）。

// 取值刻意用小写 "zh" | "en"（非 SCREAMING_SNAKE_CASE），因为这是 BCP 47 语言子标签，
// 会逐字出现在 URL 路径段 /en/ 与 html lang 属性，标准要求小写；故不套用 enum-naming.md。
export type Locale = "zh" | "en";

const STORAGE_KEY = "lane-locale";

/** URL → 语言。/enterprise 这类前缀相同的路径不算英文，故不能只用 startsWith("/en") */
export function localeFromPath(path: string): Locale {
  return path === "/en" || path.startsWith("/en/") ? "en" : "zh";
}

/** 语言 → URL。英文带尾斜杠，与 vite-ssg nested 预渲染出的 /en/index.html 形态一致 */
export function localePath(l: Locale): string {
  return l === "zh" ? "/" : "/en/";
}

/** 只在用户手动切换语言时写入；回访时 App.vue 据此把 / 跳到 /en/。
    调用方必须保证在浏览器里执行（onMounted 之后），构建期没有 localStorage */
export function rememberLocale(l: Locale): void {
  localStorage.setItem(STORAGE_KEY, l);
}

/** 读回显式选择过的语言；没存过或存着不认识的值都给 null */
export function savedLocale(): Locale | null {
  const saved = localStorage.getItem(STORAGE_KEY);
  return saved === "zh" || saved === "en" ? saved : null;
}
