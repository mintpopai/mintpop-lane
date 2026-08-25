<script setup lang="ts">
import { onMounted } from "vue";
import { useHead } from "@unhead/vue";
import { useRoute, useRouter } from "vue-router";
import TheHeader from "./sections/TheHeader.vue";
import TheFooter from "./sections/TheFooter.vue";
import { localePath, provideI18n, savedLocale } from "./i18n";

const route = useRoute();
const router = useRouter();

const { locale, t, htmlLang } = provideI18n();

const SITE = "https://lane.mintpop.ai";

// head 按语言输出，全部交给 unhead（index.html 只留与语言无关的静态项）：
// - lang / title / description / og / twitter / JSON-LD 随 locale 切换；
// - canonical 指向当前语言版本，hreflang 三连让搜索引擎把 / 与 /en/ 当同一内容的两个语言版分别收录，
//   x-default 兜底给未匹配语言的用户（指默认中文页）。
useHead({
  htmlAttrs: { lang: htmlLang },
  title: () => t.value.meta.title,
  link: () => [
    { rel: "canonical", href: SITE + localePath(locale.value) },
    { rel: "alternate", hreflang: "zh-CN", href: `${SITE}/` },
    { rel: "alternate", hreflang: "en", href: `${SITE}/en/` },
    { rel: "alternate", hreflang: "x-default", href: `${SITE}/` },
  ],
  meta: () => {
    const { title, description } = t.value.meta;
    return [
      { name: "description", content: description },
      { property: "og:title", content: title },
      { property: "og:description", content: description },
      { property: "og:url", content: SITE + localePath(locale.value) },
      { property: "og:locale", content: locale.value === "zh" ? "zh_CN" : "en_US" },
      { property: "og:image:alt", content: title },
      { name: "twitter:title", content: title },
      { name: "twitter:description", content: description },
    ];
  },
  // 结构化数据也按语言输出：原先静态放 index.html，英文页会带中文 name/description 与 inLanguage
  script: () => [
    {
      type: "application/ld+json",
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        name: "MintPop Lane",
        applicationCategory: "DeveloperApplication",
        operatingSystem: "macOS (Apple silicon), Windows 10/11 x64",
        url: SITE + localePath(locale.value),
        downloadUrl: `${SITE}${localePath(locale.value)}#download`,
        description: t.value.meta.description,
        inLanguage: locale.value === "zh" ? "zh-CN" : "en",
        author: { "@type": "Organization", name: "MintPop", url: "https://mintpop.ai" },
      }),
    },
  ],
});

// 回访偏好：存过 en 且落在默认中文页时跳到 /en/。
// 放在 onMounted（水合完成后）执行，避免首帧路由变化造成水合失配；
// 只做 / → /en/ 单向，显式访问 /en/ 永远尊重 URL；也不读 navigator.language，不做自动语言探测。
onMounted(() => {
  if (route.path === "/" && savedLocale() === "en") router.replace("/en/");
});
</script>

<template>
  <TheHeader />
  <RouterView />
  <TheFooter />
</template>
