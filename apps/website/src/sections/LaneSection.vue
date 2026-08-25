<script setup lang="ts">
import LanePathGraphic from "../components/LanePathGraphic.vue";
import { useI18n } from "../i18n";

const { t } = useI18n();
</script>

<template>
  <section id="lane" class="section section-soft">
    <div class="container">
      <p class="kicker">{{ t.lane.kicker }}</p>
      <h2 class="section-title">{{ t.lane.title }}</h2>
      <p class="section-lede">{{ t.lane.lede }}</p>

      <div class="diagram">
        <!-- 图本身按内容收缩，这里给一个上限宽度让两段连线拉开，读起来才像一条「路」 -->
        <LanePathGraphic class="graphic" variant="active" size="lg" />
        <p class="caption">{{ t.ui.lane.caption }}</p>
      </div>

      <div class="grid">
        <article v-for="p in t.lane.points" :key="p.title" class="cell">
          <h3>{{ p.title }}</h3>
          <p>{{ p.body }}</p>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.diagram {
  margin: 48px 0 56px;
  padding: 40px 32px 32px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 26px;
}

.graphic {
  width: 100%;
  max-width: 660px;
}

.caption {
  max-width: 34em;
  text-align: center;
  font-size: 14px;
  color: var(--ink-3);
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.cell {
  padding: 26px 24px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.cell h3 {
  font-size: 18px;
  margin-bottom: 10px;
}

.cell p {
  font-size: 15px;
  color: var(--ink-2);
}

@media (max-width: 860px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .diagram {
    padding: 32px 18px 26px;
    overflow-x: auto;
  }
}
</style>
