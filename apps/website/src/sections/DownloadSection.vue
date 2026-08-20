<script setup lang="ts">
import { useRelease } from "../composables/useRelease";

const { version, urlFor, releasesLatest } = useRelease();
</script>

<template>
  <section id="download" class="download">
    <div class="container">
      <h2>下载 MintPop Lane</h2>
      <p class="version">
        <template v-if="version">
          最新版本 <span class="mono">v{{ version }}</span>
        </template>
        <template v-else>安装包托管在 GitHub Releases</template>
      </p>
      <div class="grid">
        <a class="platform" :href="urlFor('MAC_ARM')">
          <h3>macOS</h3>
          <p>Apple 芯片（M 系列）</p>
          <span class="file mono">.dmg</span>
        </a>
        <a class="platform" :href="urlFor('WINDOWS')">
          <h3>Windows</h3>
          <p>x64 安装器</p>
          <span class="file mono">.exe</span>
        </a>
      </div>
      <p class="note">
        使用需要账号与有效订阅。历史版本见
        <a :href="releasesLatest" target="_blank" rel="noopener">GitHub Releases</a>。
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

.note a {
  color: var(--brand-text);
}

@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
