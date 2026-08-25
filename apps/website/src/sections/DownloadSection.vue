<script setup lang="ts">
import { computed } from "vue";
import { formatSize } from "../composables/release";
import { useRelease } from "../composables/useRelease";
import { useI18n } from "../i18n";

const { t } = useI18n();
const { version, platformFor } = useRelease();

const mac = computed(() => platformFor("MAC_ARM"));
const win = computed(() => platformFor("WINDOWS"));
// 体积拿不到（清单缺 size 或还没加载）就不渲染这一段，不出「约 0 MB」
const macSize = computed(() => formatSize(mac.value?.size ?? 0));
const winSize = computed(() => formatSize(win.value?.size ?? 0));
</script>

<template>
  <section id="download" class="section section-soft">
    <div class="container">
      <p class="kicker">{{ t.download.kicker }}</p>
      <h2 class="section-title">{{ t.download.title }}</h2>
      <p class="version">
        <template v-if="version">
          最新版本 <span class="mono">v{{ version }}</span>
        </template>
        <template v-else>正在获取最新版本…</template>
      </p>

      <div class="grid">
        <a class="platform" :class="{ 'is-unavailable': !mac }" :href="mac?.url">
          <span class="os">macOS</span>
          <span class="arch">Apple 芯片（M 系列）</span>
          <span class="file mono">
            .dmg<template v-if="macSize"> · {{ macSize }}</template>
          </span>
        </a>
        <a class="platform" :class="{ 'is-unavailable': !win }" :href="win?.url">
          <span class="os">Windows</span>
          <span class="arch">x64 安装器</span>
          <span class="file mono">
            .exe<template v-if="winSize"> · {{ winSize }}</template>
          </span>
        </a>
      </div>

      <p v-if="!mac && !win" class="unavailable">{{ t.download.unavailable }}</p>

      <dl class="req">
        <template v-for="r in t.download.requirements" :key="r.platform">
          <dt>{{ r.platform }}</dt>
          <dd>{{ r.body }}</dd>
        </template>
      </dl>

      <p class="note">{{ t.download.note }}</p>
    </div>
  </section>
</template>

<style scoped>
.version {
  margin-top: 14px;
  color: var(--ink-2);
}

.grid {
  margin-top: 32px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
  max-width: 720px;
}

.platform {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-decoration: none;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 26px 28px;
  background: var(--bg);
  transition:
    border-color 0.15s ease,
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.platform:hover {
  border-color: var(--mint-deep);
  box-shadow: 0 10px 30px -18px rgba(11, 11, 12, 0.3);
}

.platform:active {
  transform: translateY(1px);
}

/* 拿不到直链时的不可用态：无 href 的 <a> 本就不可点，这里把它在视觉上也表达清楚 */
.platform.is-unavailable {
  opacity: 0.55;
  cursor: default;
}

.platform.is-unavailable:hover {
  border-color: var(--line);
  box-shadow: none;
}

.platform.is-unavailable:active {
  transform: none;
}

.os {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 21px;
}

.arch {
  color: var(--ink-2);
  font-size: 15px;
}

.file {
  margin-top: 10px;
  font-size: 13px;
  color: var(--brand-text);
}

.unavailable {
  margin-top: 20px;
  font-size: 14px;
  color: var(--danger);
}

.req {
  margin: 36px 0 0;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 20px;
  max-width: 720px;
  font-size: 14px;
}

.req dt {
  font-weight: 600;
  color: var(--ink);
}

.req dd {
  margin: 0;
  color: var(--ink-2);
}

.note {
  margin-top: 24px;
  font-size: 14px;
  color: var(--ink-3);
}

@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
