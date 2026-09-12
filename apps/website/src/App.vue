<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useHead } from "@unhead/vue";
import { useRoute, useRouter } from "vue-router";
import TheHeader from "./sections/TheHeader.vue";
import TheFooter from "./sections/TheFooter.vue";
import { provideI18n, savedLocale, switchLocalePath } from "./i18n";
import { SITE } from "./routes";

const route = useRoute();
const router = useRouter();

const { locale, htmlLang } = provideI18n();

// 当前页在两种语言下的路径（恒带尾斜杠），canonical / hreflang / og:url 都从这两个值拼
const zhPath = computed(() => switchLocalePath(route.path, "zh"));
const enPath = computed(() => switchLocalePath(route.path, "en"));
const selfPath = computed(() => (locale.value === "zh" ? zhPath.value : enPath.value));

// 这里只放「与路径相关、每个页面都一样算法」的 head：
// - lang 随 locale；
// - canonical 指向当前语言版本，hreflang 三连让搜索引擎把中英两版当同一内容的两个语言版分别收录，
//   x-default 兜底给未匹配语言的用户（指中文版）；
// - og:url / og:locale 与两张按语言排版的社交卡片图。
// title / description / og:title / og:description 与 JSON-LD 由各页面自己输出（HomePage / GuidePage）。
useHead({
  htmlAttrs: { lang: htmlLang },
  link: () => [
    { rel: "canonical", href: SITE + selfPath.value },
    { rel: "alternate", hreflang: "zh-CN", href: SITE + zhPath.value },
    { rel: "alternate", hreflang: "en", href: SITE + enPath.value },
    { rel: "alternate", hreflang: "x-default", href: SITE + zhPath.value },
  ],
  meta: () => {
    // 社交卡片图按语言分两张：og.png 是中文排版，og-en.png 是英文排版（图是二进制，
    // 「英文页不该有中文」那条验收扫不到它，故要在这里按语言分开）
    const ogImage = `${SITE}/${locale.value === "zh" ? "og.png" : "og-en.png"}`;
    return [
      { property: "og:url", content: SITE + selfPath.value },
      { property: "og:locale", content: locale.value === "zh" ? "zh_CN" : "en_US" },
      // 让社交平台知道另一语言版存在
      { property: "og:locale:alternate", content: locale.value === "zh" ? "en_US" : "zh_CN" },
      { property: "og:image", content: ogImage },
      { property: "og:image:width", content: "1200" },
      { property: "og:image:height", content: "630" },
      { name: "twitter:image", content: ogImage },
    ];
  },
});

// 回访偏好：存过 en 且落在默认中文首页时跳到 /en/。
// 放在 onMounted（水合完成后）执行，避免首帧路由变化造成水合失配；
// 只做 / → /en/ 单向，显式访问 /en/ 或任何指南页永远尊重 URL；也不读 navigator.language，不做自动语言探测。
onMounted(() => {
  if (route.path === "/" && savedLocale() === "en") router.replace("/en/");
});
</script>

<template>
  <TheHeader />
  <RouterView />
  <TheFooter />
</template>
