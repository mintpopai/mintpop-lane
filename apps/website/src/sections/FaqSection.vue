<script setup lang="ts">
import { faq } from "../content/copy";
</script>

<template>
  <section id="faq" class="section-tight section-line">
    <div class="container">
      <p class="kicker">{{ faq.kicker }}</p>
      <h2 class="section-title">{{ faq.title }}</h2>

      <!-- 用原生 details：无 JS、键盘可达、搜索引擎能读到答案正文 -->
      <div class="list">
        <details v-for="item in faq.items" :key="item.q">
          <summary>
            <span class="q">{{ item.q }}</span>
            <span class="sign" aria-hidden="true"></span>
          </summary>
          <p class="a">{{ item.a }}</p>
        </details>
      </div>
    </div>
  </section>
</template>

<style scoped>
.list {
  margin-top: 40px;
  max-width: 800px;
  border-top: 1px solid var(--line);
}

details {
  border-bottom: 1px solid var(--line);
}

summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 2px;
  cursor: pointer;
  list-style: none;
}

summary::-webkit-details-marker {
  display: none;
}

.q {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 18px;
}

/* ＋ / − 号用两条线拼，展开时竖线转没 */
.sign {
  margin-left: auto;
  position: relative;
  width: 14px;
  height: 14px;
  flex: none;
}

.sign::before,
.sign::after {
  content: "";
  position: absolute;
  background: var(--ink-3);
  border-radius: 1px;
}

.sign::before {
  inset: 6px 0 6px 0;
  height: 2px;
}

.sign::after {
  inset: 0 6px 0 6px;
  width: 2px;
  transition: transform 0.18s ease;
}

details[open] .sign::after {
  transform: scaleY(0);
}

details[open] .q {
  color: var(--brand-text);
}

.a {
  padding: 0 2px 22px;
  max-width: 46em;
  color: var(--ink-2);
  font-size: 15.5px;
}

@media (prefers-reduced-motion: reduce) {
  .sign::after {
    transition: none;
  }
}
</style>
