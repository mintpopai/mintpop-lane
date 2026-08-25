<script setup lang="ts">
import { security } from "../content/copy";
</script>

<template>
  <section id="security" class="section section-soft">
    <div class="container">
      <p class="kicker">{{ security.kicker }}</p>
      <h2 class="section-title">{{ security.title }}</h2>
      <p class="section-lede">{{ security.lede }}</p>

      <ol class="grid">
        <li v-for="(item, i) in security.items" :key="item.title" class="cell">
          <span class="idx mono">{{ String(i + 1).padStart(2, "0") }}</span>
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.body }}</p>
          </div>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.grid {
  list-style: none;
  margin: 48px 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.cell {
  display: flex;
  gap: 18px;
  padding: 26px 24px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.idx {
  flex: none;
  font-size: 13px;
  font-weight: 500;
  color: var(--brand-text);
  padding-top: 3px;
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
}
</style>
