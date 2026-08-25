// 语言的单一来源是 URL：/ 是中文，/en/ 是英文。
// 这里只放不依赖 vue-router 的纯逻辑；provide/inject 那部分见本文件后半（Task 4 补）。

import { computed, inject, provide, ref, watch, type InjectionKey, type Ref } from "vue";
import { useRoute } from "vue-router";
import { COPY, type Copy } from "./content/copy";

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
    调用方必须保证在浏览器里执行（onMounted 之后），构建期没有 localStorage。
    iOS「阻止所有 Cookie」等隐私模式下访问 localStorage 不是「读不到」而是直接抛
    SecurityError；这里在 router.push 之前调用，不兜住的话会连语言都切不了，
    故失败静默忽略——记不住偏好总比切换语言整个失效轻 */
export function rememberLocale(l: Locale): void {
  try {
    localStorage.setItem(STORAGE_KEY, l);
  } catch {
    // 忽略：写入失败只是下次回访记不住语言偏好，不影响本次切换
  }
}

/** 读回显式选择过的语言；没存过、存着不认识的值、或访问本身抛异常（同上）都给 null */
export function savedLocale(): Locale | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved === "zh" || saved === "en" ? saved : null;
  } catch {
    return null;
  }
}

const LOCALE_KEY: InjectionKey<Ref<Locale>> = Symbol("locale");

// 在根组件（App.vue）的 setup 里调用一次：locale 挂在 app 实例上（provide），**不做模块单例**——
// vite-ssg 会在同一进程里**并发**预渲染多条路由，模块级可变状态会被其它路由的渲染改写
// （跨请求状态污染：典型症状是中文页序列化出英文 head）。每个 app 实例一份，天然隔离。
export function provideI18n() {
  const route = useRoute();
  const locale = ref<Locale>(localeFromPath(route.path));
  watch(
    () => route.path,
    (p) => (locale.value = localeFromPath(p)),
  );
  provide(LOCALE_KEY, locale);
  return makeI18n(locale);
}

/** 子组件取用（App.vue 已 provide，注入必然成功） */
export function useI18n() {
  const locale = inject(LOCALE_KEY);
  if (!locale) throw new Error("useI18n 必须在 App.vue（provideI18n）之下使用");
  return makeI18n(locale);
}

function makeI18n(locale: Ref<Locale>) {
  const t = computed<Copy>(() => COPY[locale.value]);
  const htmlLang = computed(() => (locale.value === "zh" ? "zh-CN" : "en"));
  return { locale, t, htmlLang };
}
