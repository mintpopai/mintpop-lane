<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { localePath, rememberLocale, useI18n } from "../i18n";

const { t, locale } = useI18n();
const router = useRouter();

// 语言切换 = 路由跳转（/ ↔ /en/），URL 即语言；同时记住偏好，供回访时 App.vue 自动跳转
function toggleLocale() {
  const next = locale.value === "zh" ? "en" : "zh";
  rememberLocale(next);
  router.push(localePath(next));
}

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
      <!-- 品牌锁定组合：Lane 应用瓦片 + 官方词标 + 分隔线 + 产品名。
           品牌部分一律用官方词标图，不用文字排 logo（品牌规范 INVARIANT）；产品名 Lane 才是文字。
           浅底 → 深字版词标；瓦片与词标都直接引品牌规范站，不落地到本仓库，规范站换图这边跟着变。
           瓦片 alt 留空：它与紧随其后的词标表达的是同一件事，读屏念两遍反而啰嗦，
           链接本身的 aria-label 已说清这是「MintPop Lane 首页」 -->
      <a class="brand" href="/" :aria-label="t.ui.header.homeLabel">
        <img
          class="app-icon"
          src="https://standards.mintpop.ai/assets/products/lane/lane-app-cloud.png"
          alt=""
          width="36"
          height="36"
        />
        <img
          class="wordmark"
          src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-dark.png"
          alt="MintPop"
          width="106"
          height="29"
        />
        <span class="product">Lane</span>
      </a>

      <nav class="nav" :aria-label="t.ui.header.navLabel">
        <a v-for="item in t.nav" :key="item.href" :href="item.href">{{ item.label }}</a>
      </nav>

      <!-- 放在 .nav 外面：<860px 时 .nav 整个 display:none，
           切换按钮若在里面，手机上就没法切语言了——而手机正是英文访客最可能的入口 -->
      <button class="lang" type="button" :aria-label="t.ui.header.langSwitchLabel" @click="toggleLocale">
        {{ t.ui.header.langToggle }}
      </button>

      <a class="btn btn-primary btn-sm cta" href="#download">{{ t.ui.header.cta }}</a>
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

/* 应用瓦片自带约 20% 安全区（图形真身比外框小一圈），故取 36px——比 26px 的词标高一档，
   两者的「视觉重量」才对得上。瓦片底色是 Cloud，在白色顶栏上是一个看得见的浅色方块，
   故左缘按「方块本身」对齐容器左边（不做负外边距）；右侧则抵掉那圈留白，
   让它与词标的实际间距回到 14px，不至于看着被推远 */
.app-icon {
  width: 36px;
  height: 36px;
  display: block;
  margin-right: -6px;
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

/* 与顶栏其它文字同级的朴素文字按钮：它是一个低频开关，不该抢下载 CTA 的注意力 */
.lang {
  flex: none;
  margin-left: 24px;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: var(--ink-2);
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease;
}

.lang:hover {
  color: var(--ink);
  border-color: var(--ink-3);
}

.cta {
  flex: none;
}

@media (max-width: 860px) {
  .nav {
    display: none;
  }
  .lang {
    margin-left: auto;
  }
  .cta {
    margin-left: 12px;
  }
}
</style>
