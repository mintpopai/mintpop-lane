<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { localePath, rememberLocale, useI18n } from "../i18n";

const { t, locale } = useI18n();
const router = useRouter();
const route = useRoute();

// 语言切换 = 路由跳转（/ ↔ /en/），URL 即语言；同时记住偏好，供回访时 App.vue 自动跳转。
// 带上当前 hash：否则滚到 FAQ 时点切换会被扔回页顶（B2.2）
function toggleLocale() {
  const next = locale.value === "zh" ? "en" : "zh";
  rememberLocale(next);
  router.push({ path: localePath(next), hash: route.hash });
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
      <!-- 品牌链接跟随当前语言（/ 或 /en/），不硬编码 "/"：否则从搜索引擎直落 /en/ 的
           英文访客（没有 localStorage 偏好）点一下 logo 就会被整页带回中文站（A2）。
           用 RouterLink 而非 <a> 还省一次整页刷新。 -->
      <RouterLink class="brand" :to="localePath(locale)" :aria-label="t.ui.header.homeLabel">
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
      </RouterLink>

      <nav class="nav" :aria-label="t.ui.header.navLabel">
        <a v-for="item in t.nav" :key="item.href" :href="item.href">{{ item.label }}</a>
      </nav>

      <!-- 放在 .nav 外面：<860px 时 .nav 整个 display:none，
           切换按钮若在里面，手机上就没法切语言了——而手机正是英文访客最可能的入口 -->
      <button class="lang" type="button" :aria-label="t.ui.header.langSwitchLabel" @click="toggleLocale">
        {{ t.ui.header.langToggle }}
      </button>

      <!-- 联系页在 MintPop 主站（mintpop.ai）上，与官网不同域，故用普通 <a> 而非 RouterLink。
           新标签页打开：看完联系方式回来，官网这一页还在原处（含滚动位置）。
           样式取描边次级按钮——它是并列入口，不该与实心的下载 CTA 抢注意力。
           aria-label 里说明「在新标签页打开」，读屏用户不至于被突然的新窗口打断。 -->
      <a
        class="btn btn-ghost btn-sm contact"
        :href="t.ui.contact.href"
        target="_blank"
        rel="noopener noreferrer"
        :aria-label="t.ui.contact.ariaLabel"
        >{{ t.ui.contact.label }}</a
      >

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
  /* .brand 是 flex 项且内含固定尺寸图片，默认 min-width:auto 会让它无法收缩，
     是窄屏下整页横向溢出的根因（A1）。加上后最坏情况是品牌锁定组合被裁切，
     而不是整页出现横向滚动条。 */
  min-width: 0;
  overflow: hidden;
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

.contact,
.cta {
  flex: none;
}

/* 顶栏按「谁最先让路」分两档收：先收联系按钮，再收导航。
   联系入口在页脚也有一份，导航项则只此一处，故联系先让路。

   第一档：加上联系按钮后，整行最小需求宽实测为 中文 922px / 英文 999px
   （英文 "Contact us" 连按钮 108px，比「联系我们」宽 18px，加上 20px 间距共 128px）。
   卡在 1000px 只剩 1px 余量，字体渲染差个几像素就撑破，故取 1040px 留 41px 余量。 */
@media (max-width: 1040px) {
  .contact {
    display: none;
  }
}

/* 第二档：导航加到 4 项后，整行的最小需求宽是 中文 800px / 英文 888px（英文项名更宽，
   "Built-in terminal" 一项就 116px）。原来的 860px 断点只够 3 项，英文页在 861–941px
   之间会把顶栏撑破，故抬到 920px——英文页 888px 也留得下 33px 余量。
   此档联系按钮早已在上一档收起，这里不必再管它。 */
@media (max-width: 920px) {
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

/* 真实手机视口（iPhone 常见 390px、部分安卓 360px）下，即便 .nav 已隐藏，
   brand + lang + cta 的最小内容宽仍会顶穿页面。先隐藏 .product（竖线 + "Lane" 文字）省下约 59px。 */
@media (max-width: 520px) {
  .product {
    display: none;
  }
}

/* 再窄就连「瓦片 + 词标」这个完整锁定组合都放不下了。实测宽度：瓦片 36 + 间距 14 + 词标 95 = 145px，
   加上容器留白 48、两处 20 的间距、切换按钮 62、下载按钮左边距 12 与按钮本身，
   完整组合需要 中文页 369px / 英文页 408px（英文的 "Download" 比「下载」宽 39px，所以英文先撑不住）。
   .brand 的 overflow: hidden 只保证页面不横向滚动，代价是**词标被拦腰切断**——
   实测 390px 英文页会渲染成半截的 "mintpo"，这比不显示词标更伤品牌。
   故在 430px 以下只保留应用瓦片（它同样是品牌规范站上的官方素材，不是用文字排 logo），
   此时英文页只需 299px，320px 的窄机也放得下。链接的 aria-label 仍说明这是首页入口，读屏不受影响。 */
@media (max-width: 430px) {
  .wordmark {
    display: none;
  }
  /* 那 -6px 只为拉近瓦片与词标的实际间距而存在；词标一隐藏它就没了意义，
     反而会把 .brand 的内容宽压到 30px，被 overflow: hidden 切掉瓦片右缘 6px（圆角与网点缺一块）。
     这里归零，让瓦片完整显示。 */
  .app-icon {
    margin-right: 0;
  }
}
</style>
