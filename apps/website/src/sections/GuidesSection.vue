<script setup lang="ts">
import { GUIDES } from "../content/guides";
import { useI18n } from "../i18n";
import { guidePath } from "../routes";

const { t, locale } = useI18n();
</script>

<template>
  <section id="guides" class="section-tight section-soft section-line">
    <div class="container">
      <p class="kicker">{{ t.guides.kicker }}</p>
      <h2 class="section-title">{{ t.guides.title }}</h2>
      <p class="section-lede">{{ t.guides.lede }}</p>

      <!-- 首页对指南的内链：条目来自注册表，与页脚同源；搜索引擎从首页发现指南页主要靠这里 -->
      <ul class="grid">
        <li v-for="g in GUIDES" :key="g.slug">
          <RouterLink :to="guidePath(locale, g.slug)">
            <h3>{{ g.copy[locale].h1 }}</h3>
            <p>{{ g.copy[locale].summary }}</p>
          </RouterLink>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.grid {
  list-style: none;
  margin: 40px 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.grid a {
  display: block;
  height: 100%;
  padding: 24px;
  border-radius: var(--radius);
  background: var(--bg);
  border: 1px solid var(--line);
  text-decoration: none;
  transition: border-color 0.15s ease;
}

.grid a:hover {
  border-color: var(--brand-text);
}

.grid h3 {
  font-size: 19px;
  line-height: 1.35;
}

.grid p {
  margin-top: 10px;
  font-size: 15px;
  color: var(--ink-2);
}

@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
