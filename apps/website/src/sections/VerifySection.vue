<script setup lang="ts">
import LanePathGraphic from "../components/LanePathGraphic.vue";
import SituationCard from "../components/SituationCard.vue";
import { verify } from "../content/copy";
</script>

<template>
  <section id="verify" class="section section-line">
    <div class="container">
      <p class="kicker">{{ verify.kicker }}</p>
      <h2 class="section-title">{{ verify.title }}</h2>
      <p class="section-lede">{{ verify.lede }}</p>

      <!-- 安全不变量：单独拎出来，它是整个产品的地基 -->
      <div class="invariant">
        <div class="invariant-copy">
          <span class="tag mono">{{ verify.invariant.label }}</span>
          <p>{{ verify.invariant.body }}</p>
        </div>
        <div class="invariant-graphic">
          <LanePathGraphic variant="mismatch" :labels="false" />
          <p class="graphic-note">出口不符 → 暂停放行</p>
        </div>
      </div>

      <div class="honesty">
        <h3>{{ verify.honesty.title }}</h3>
        <p>{{ verify.honesty.body }}</p>
      </div>

      <div class="grid">
        <SituationCard v-for="s in verify.situations" :key="s.code" v-bind="s" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.invariant {
  margin-top: 48px;
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 32px;
  align-items: center;
  padding: 32px 34px;
  border-radius: var(--radius);
  background: linear-gradient(140deg, var(--bg-mint) 0%, var(--bg-soft) 100%);
  border: 1px solid var(--line-mint);
}

.tag {
  display: inline-block;
  margin-bottom: 12px;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--brand-strong);
}

.invariant-copy p {
  font-size: 17px;
  color: var(--ink);
  max-width: 34em;
}

.invariant-graphic {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.graphic-note {
  font-size: 13px;
  color: var(--warn);
  font-weight: 500;
}

.honesty {
  margin: 64px 0 32px;
  max-width: 46em;
}

.honesty h3 {
  font-size: clamp(21px, 2.6vw, 26px);
  margin-bottom: 14px;
}

.honesty p {
  color: var(--ink-2);
  font-size: 16px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
}

@media (max-width: 900px) {
  .invariant {
    grid-template-columns: 1fr;
  }
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
