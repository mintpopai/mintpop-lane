<script setup lang="ts">
import { computed } from "vue";
import { useRelease } from "../composables/useRelease";

const { primaryKey, urlFor, releasesLatest } = useRelease();

/** 主按钮按访客系统给平台直链；识别不出系统时兜底到 Releases 页 */
const primaryHref = computed(() =>
  primaryKey.value ? urlFor(primaryKey.value) : releasesLatest,
);
const primaryLabel = computed(() => {
  if (primaryKey.value === "MAC_ARM") return "下载 macOS 版";
  if (primaryKey.value === "WINDOWS") return "下载 Windows 版";
  return "前往下载";
});
</script>

<template>
  <section class="hero">
    <div class="container">
      <h1 class="rise">
        登录即用的
        <span class="accent">AI 编码终端</span>
      </h1>
      <p class="sub rise">
        打开应用登录，专属链路自动接入，订阅内的 Agent 服务自动配好，
        在内置终端里直接开始写代码。
      </p>
      <div class="cta rise-late">
        <a class="btn btn-primary" :href="primaryHref">{{ primaryLabel }}</a>
        <a class="btn btn-ghost" href="#download">全部安装包</a>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  /* 顶部留白克制：内容不悬到视口中段 */
  padding: 88px 0 96px;
  background:
    radial-gradient(680px 340px at 85% 0%, var(--bg-mint) 0%, transparent 70%),
    var(--bg);
}

h1 {
  font-size: clamp(38px, 6vw, 60px);
  max-width: 15em;
}

.accent {
  color: var(--brand-text);
}

.sub {
  margin-top: 20px;
  font-size: 18px;
  color: var(--ink-2);
  max-width: 34em;
}

.cta {
  margin-top: 36px;
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}
</style>
