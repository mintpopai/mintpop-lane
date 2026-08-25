<script setup lang="ts">
import TheHeader from "./sections/TheHeader.vue";
import TheFooter from "./sections/TheFooter.vue";
import { provideI18n } from "./i18n";

// locale 由路由派生、挂在本 app 实例上（provide）——不能用模块单例，见 i18n.ts 注释。
// head 的按语言输出在 Task 7 补上，这里先只做注入。
provideI18n();
</script>

<template>
  <TheHeader />
  <RouterView />
  <TheFooter />
</template>
