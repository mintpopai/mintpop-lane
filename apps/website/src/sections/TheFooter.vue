<script setup lang="ts">
import { useI18n } from "../i18n";

const { t } = useI18n();
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
        <p class="tagline">{{ t.footer.tagline }}</p>
      </div>

      <nav class="links" :aria-label="t.ui.footer.navLabel">
        <a v-for="l in t.footer.links" :key="l.href" :href="l.href">{{ l.label }}</a>
        <!-- 跨站到 MintPop 主站，和页内锚点不是一回事，故单列在 v-for 之外，
             并与页头同一份 t.ui.contact（同一个链接不写两遍）。
             ≤1040px 顶栏会把联系按钮收起（见 TheHeader.vue 的断点注释），那时这里就是唯一的联系入口。 -->
        <a
          :href="t.ui.contact.href"
          target="_blank"
          rel="noopener noreferrer"
          :aria-label="t.ui.contact.ariaLabel"
          >{{ t.ui.contact.label }}</a
        >
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

/* 加到 4 项后这一行的最小需求宽是 中文 274px / 英文 345px，而 320px 窄机上版心只有 272px、
   390px 的 iPhone 也只有 342px——不换行就会整页横向溢出（页头 A1 是同一类问题）。
   故允许折行，并给一档行间距，免得折下来的那行贴上去。 */
.links {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 26px;
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
