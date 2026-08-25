<script setup lang="ts">
import { onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import TheHeader from "./sections/TheHeader.vue";
import TheFooter from "./sections/TheFooter.vue";
import { provideI18n, savedLocale } from "./i18n";

const route = useRoute();
const router = useRouter();

// locale 由路由派生、挂在本 app 实例上（provide）——不能用模块单例，见 i18n.ts 注释。
// head 的按语言输出在 Task 7 补上，这里先只做注入。
provideI18n();

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
