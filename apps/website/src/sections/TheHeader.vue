<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { nav } from "../content/copy";

/** 滚过首屏就给顶栏加边框与更实的底：不滚动时让它彻底融进 Hero 的留白里 */
const scrolled = ref(false);
function onScroll() {
  scrolled.value = window.scrollY > 8;
}
onMounted(() => {
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });
});
onUnmounted(() => window.removeEventListener("scroll", onScroll));
</script>

<template>
  <header :class="['header', { scrolled }]">
    <div class="container bar">
      <!-- 品牌部分一律用官方词标图，不用文字排 logo（品牌规范 INVARIANT）；
           产品名 Lane 才是文字。浅底 → 深字版词标 -->
      <a class="brand" href="/" aria-label="MintPop Lane 首页">
        <img
          class="wordmark"
          src="/brand/wordmark/mintpop-wordmark-dark.png"
          alt="MintPop"
          width="106"
          height="29"
        />
        <span class="product">Lane</span>
      </a>

      <nav class="nav" aria-label="主导航">
        <a v-for="item in nav" :key="item.href" :href="item.href">{{ item.label }}</a>
      </nav>

      <a class="btn btn-primary btn-sm cta" href="#download">下载</a>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid transparent;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.header.scrolled {
  border-bottom-color: var(--line);
}

.bar {
  height: 68px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  text-decoration: none;
}

/* 词标是原图抠出的透明底 PNG：等比缩放，不加描边/阴影/圆角，高度不低于 20px */
.wordmark {
  height: 26px;
  width: auto;
  display: block;
}

/* 产品名与品牌词标之间用一条细分隔线，避免读成「mintpoplane」一个词 */
.product {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 20px;
  letter-spacing: -0.01em;
  padding-left: 14px;
  border-left: 1px solid var(--line);
  line-height: 1.2;
}

.nav {
  margin-left: auto;
  display: flex;
  gap: 30px;
  font-size: 15px;
}

.nav a {
  text-decoration: none;
  color: var(--ink-2);
  transition: color 0.15s ease;
}

.nav a:hover {
  color: var(--ink);
}

.cta {
  flex: none;
}

@media (max-width: 860px) {
  .nav {
    display: none;
  }
  .cta {
    margin-left: auto;
  }
}
</style>
