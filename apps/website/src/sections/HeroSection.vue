<script setup lang="ts">
import { computed } from "vue";
import LaneVisual from "../components/LaneVisual.vue";
import { useI18n } from "../i18n";
import { useRelease } from "../composables/useRelease";

const { t } = useI18n();
const { primaryKey, platformFor, version } = useRelease();

// 主按钮按访客系统给平台直链。识别不出系统、或清单还没到手/拉取失败时，
// 兜底到下载区锚点——那里会如实说明当前状态。不再兜底到 GitHub Releases 页（仓库已私有，那页 404）。
const primary = computed(() => (primaryKey.value ? platformFor(primaryKey.value) : null));
const primaryHref = computed(() => primary.value?.url ?? "#download");
const primaryLabel = computed(() => {
  if (!primary.value) return t.value.ui.hero.primaryFallback;
  if (primaryKey.value === "MAC_ARM") return t.value.ui.hero.primaryMac;
  if (primaryKey.value === "WINDOWS") return t.value.ui.hero.primaryWin;
  return t.value.ui.hero.primaryFallback;
});
</script>

<template>
  <section class="hero">
    <div class="container">
      <div class="copy">
        <p class="pill rise">
          <span class="led" aria-hidden="true"></span>
          {{ t.hero.pill }}
        </p>

        <h1 class="rise rise-1">
          {{ t.hero.title[0] }}<br />
          <span class="accent">{{ t.hero.title[1] }}</span>
        </h1>

        <p class="lede rise rise-2">
          {{ t.hero.lede[0] }}<br />
          {{ t.hero.lede[1] }}
        </p>

        <div class="cta rise rise-3">
          <a class="btn btn-primary" :href="primaryHref">{{ primaryLabel }}</a>
          <a class="btn btn-ghost" href="#download">{{ t.ui.hero.allDownloads }}</a>
        </div>

        <p class="note rise rise-3">
          <template v-if="version">
            {{ t.ui.hero.latest }} <span class="mono">v{{ version }}</span> ·
          </template>
          {{ t.hero.note }}
        </p>
      </div>

      <div class="visual rise rise-3">
        <LaneVisual />
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  padding: 84px 0 104px;
  overflow: hidden;
  background:
    radial-gradient(760px 420px at 78% -6%, var(--bg-mint) 0%, transparent 68%),
    radial-gradient(520px 320px at 6% 8%, rgba(23, 209, 167, 0.07) 0%, transparent 70%),
    var(--bg);
}

.copy {
  max-width: 760px;
}

.led {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mint-deep);
}

h1 {
  margin-top: 22px;
  font-size: clamp(34px, 5.2vw, 56px);
  letter-spacing: -0.025em;
}

.accent {
  color: var(--brand-text);
}

.lede {
  margin-top: 24px;
  font-size: 18px;
  color: var(--ink-2);
}

.cta {
  margin-top: 34px;
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}

.note {
  margin-top: 18px;
  font-size: 14px;
  color: var(--ink-3);
}

.visual {
  margin-top: 64px;
}

@media (max-width: 640px) {
  .hero {
    padding: 56px 0 72px;
  }
  .visual {
    margin-top: 44px;
  }
}
</style>
