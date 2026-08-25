<script setup lang="ts">
import { footer } from "../content/copy";

const year = new Date().getFullYear();
</script>

<template>
  <!-- 深色页脚 → 用白字版词标（品牌规范：浅底深字版、深底白字版，不许硬放反）。
       与页头一样直接引品牌规范站，不落地到本仓库 -->
  <footer class="footer">
    <div class="container bar">
      <div class="brand">
        <img
          src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-light.png"
          alt="MintPop"
          width="132"
          height="36"
        />
        <p class="tagline">{{ footer.tagline }}</p>
      </div>

      <nav class="links" aria-label="页脚导航">
        <a v-for="l in footer.links" :key="l.href" :href="l.href">{{ l.label }}</a>
      </nav>
    </div>

    <div class="container legal">
      <span>© {{ year }} MintPop</span>
      <span class="product">MintPop Lane</span>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  background: var(--ink);
  color: rgba(255, 255, 255, 0.62);
  padding: 56px 0 32px;
  font-size: 14px;
}

.bar {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 36px;
}

/* 词标四周留白不小于图标高度 1/2（品牌规范） */
.brand img {
  height: 34px;
  width: auto;
  display: block;
}

.tagline {
  margin-top: 14px;
  font-family: var(--font-display);
  font-size: 15px;
  color: var(--mint-bright);
}

.links {
  display: flex;
  gap: 26px;
}

.links a {
  text-decoration: none;
  color: rgba(255, 255, 255, 0.62);
  transition: color 0.15s ease;
}

.links a:hover {
  color: #ffffff;
}

.legal {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-top: 26px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
}

.product {
  font-family: var(--font-display);
}
</style>
