<script setup lang="ts">
import { computed } from "vue";
import { formatSize } from "../composables/release";
import { useRelease } from "../composables/useRelease";

const { version, platformFor } = useRelease();

const mac = computed(() => platformFor("MAC_ARM"));
const win = computed(() => platformFor("WINDOWS"));
// 体积拿不到（清单缺 size 或还没加载）就不渲染这一段，不出「约 0 MB」
const macSize = computed(() => formatSize(mac.value?.size ?? 0));
const winSize = computed(() => formatSize(win.value?.size ?? 0));
</script>

<template>
  <section id="download" class="download">
    <div class="container">
      <h2>下载 MintPop Lane</h2>
      <p class="version">
        <template v-if="version">
          最新版本 <span class="mono">v{{ version }}</span>
        </template>
        <template v-else>正在获取最新版本…</template>
      </p>
      <div class="grid">
        <a class="platform" :class="{ 'is-unavailable': !mac }" :href="mac?.url">
          <h3>macOS</h3>
          <p>Apple 芯片（M 系列）</p>
          <span class="file mono">.dmg<template v-if="macSize"> · {{ macSize }}</template></span>
        </a>
        <a class="platform" :class="{ 'is-unavailable': !win }" :href="win?.url">
          <h3>Windows</h3>
          <p>x64 安装器</p>
          <span class="file mono">.exe<template v-if="winSize"> · {{ winSize }}</template></span>
        </a>
      </div>
      <p class="note">
        <template v-if="mac || win">使用需要账号与有效订阅。</template>
        <template v-else>下载链接暂时不可用，请稍后重试。</template>
      </p>
    </div>
  </section>
</template>

<style scoped>
.download {
  padding: 88px 0 96px;
  border-top: 1px solid var(--border);
}

h2 {
  font-size: clamp(26px, 3.6vw, 36px);
}

.version {
  margin-top: 10px;
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
  display: block;
  text-decoration: none;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 26px 28px;
  transition:
    border-color 0.15s ease,
    transform 0.15s ease;
}

.platform:hover {
  border-color: var(--brand-deep);
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
  border-color: var(--border);
}

.platform.is-unavailable:active {
  transform: none;
}

.platform h3 {
  font-size: 20px;
}

.platform p {
  margin-top: 6px;
  color: var(--ink-2);
  font-size: 15px;
}

.file {
  display: inline-block;
  margin-top: 14px;
  font-size: 13px;
  color: var(--brand-text);
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
