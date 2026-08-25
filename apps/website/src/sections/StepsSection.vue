<script setup lang="ts">
import { steps } from "../content/copy";
</script>

<template>
  <section class="section-tight section-line">
    <div class="container">
      <p class="kicker">{{ steps.kicker }}</p>
      <h2 class="section-title">{{ steps.title }}</h2>

      <ol class="list">
        <li v-for="s in steps.items" :key="s.title">
          <h3>{{ s.title }}</h3>
          <p>{{ s.body }}</p>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
/* 与上下两节的卡片网格换一种版式：带序号的横向流程 */
.list {
  list-style: none;
  margin: 44px 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 40px;
  counter-reset: step;
}

.list li {
  counter-increment: step;
  padding-top: 20px;
  border-top: 2px solid var(--mint);
}

.list li::before {
  content: counter(step, decimal-leading-zero);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 500;
  color: var(--brand-text);
  display: block;
  margin-bottom: 10px;
}

.list h3 {
  font-size: 19px;
  margin-bottom: 8px;
}

.list p {
  color: var(--ink-2);
  font-size: 15px;
}

@media (max-width: 860px) {
  .list {
    grid-template-columns: 1fr;
    gap: 28px;
  }
}
</style>
