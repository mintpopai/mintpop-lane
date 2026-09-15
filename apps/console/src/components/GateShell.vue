<script setup lang="ts">
// 闸门页（登录引导 / 无权限 / 登录错误）共用的骨架。主次按「登录卡是主角」排：
//
// · 主区占七成多，Cloud 底，登录卡浮在正中，内容居中对齐——页面上唯一的焦点。
// · 左上角 chrome 讲产品：与官网首页顶栏同一套品牌锁定组合——应用瓦片 + 官方词标
//   + 分隔线 + 产品名「Lane」，后面再缀一个小一号、灰一档的「控制台」说明这是哪。
//   品牌部分一律用词标图、不用文字排 logo（品牌规范 INVARIANT），尺寸与间距照官网
//   TheHeader.vue 的 .brand 抄，两处对齐。站点地址退到左下角，与左上角成对。
// · 右侧一条窄的主色带讲母品牌：一层气泡场 + MintPop 组织标记 + slogan。
//   气泡场是整条带子的背景层（铺满、绝对定位），品牌块压在它上层、在带子里双居中——
//   气泡从标记与 slogan 身后升上来、在顶部啵地破开，整条带读起来是一杯汽水，
//   而不是「一块图形 + 一块文字」。它演的是品牌语本身：Pop（气泡破裂）into
//   something fresh（薄荷、汽水）。气泡只是气氛，说不清自己是什么，所以品牌必须在场。
//   slogan 逐字打出来（见下方打字机），字体用品牌展示体 Fredoka——与左上角那个
//   「Lane」同一张脸，圆头圆脑，跟气泡是一路货。
//
// 左上角的词标（横版 mintpop 字样）与带子里的组织标记（方形印章）是母品牌的两种形态，
// 不是同一张图：一处认路（这是谁家的产品），一处造气氛（深墨带上的品牌印章）。
// 闸门页只回答「这是哪」与「怎么进」，产品能干什么属于官网，不在门口讲。
// 样式在 styles/layout.css 的 gate 段。

import { onBeforeUnmount, onMounted, ref } from "vue";

defineProps<{
  /** 卡片放宽一档。给正文较长的页用（排障说明），窄卡会把中文断得很碎 */
  wide?: boolean;
}>();

/** 品牌 slogan。写成常量而不是模板里的一段字：打字机要按字符切片推进 */
const SLOGAN = "Pop into something fresh";
/** 起打时刻：排在组织标记弹出（0.3s）、slogan 淡入（0.42s）之后，接着这一串错峰往下演 */
const TYPE_START_MS = 640;
/** 每字间隔。24 个字符约 1.1s 打完——快到不让人干等，慢到看得出是在「打」 */
const TYPE_STEP_MS = 46;

/** 已打出的部分。真身整句始终在 DOM 里（见模板），这里只驱动可见的那一层 */
const typedSlogan = ref("");
let startTimer: ReturnType<typeof setTimeout> | undefined;
let stepTimer: ReturnType<typeof setInterval> | undefined;

/** reduced-motion 档、以及拿不到 matchMedia 的环境（SSR / 测试），一律直接给全文 */
function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

onMounted(() => {
  if (prefersReducedMotion()) {
    typedSlogan.value = SLOGAN;
    return;
  }

  startTimer = setTimeout(() => {
    stepTimer = setInterval(() => {
      typedSlogan.value = SLOGAN.slice(0, typedSlogan.value.length + 1);
      if (typedSlogan.value.length === SLOGAN.length) {
        clearInterval(stepTimer);
        stepTimer = undefined;
      }
    }, TYPE_STEP_MS);
  }, TYPE_START_MS);
});

// 打到一半就被路由换走时把两个计时器都撤掉，别留着往已卸载的组件上写
onBeforeUnmount(() => {
  clearTimeout(startTimer);
  clearInterval(stepTimer);
});
</script>

<template>
  <div class="gate">
    <main class="gate-main">
      <!-- 品牌锁定组合，与官网首页顶栏逐项对齐。瓦片 alt 留空：它与紧随其后的词标说的是
           同一件事，读屏念两遍反而啰嗦。标了尺寸，图没到之前不抖版 -->
      <p class="gate-brand">
        <img
          class="gate-brand-icon"
          src="https://standards.mintpop.ai/assets/products/lane/lane-app-cloud.png"
          alt=""
          width="36"
          height="36"
        />
        <img
          class="gate-brand-wordmark"
          src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-dark.png"
          alt="MintPop"
          width="106"
          height="29"
        />
        <span class="gate-brand-text">
          Lane
          <!-- 「控制台」是身份说明不是产品名，跟在 Lane 后面小一号、灰一档：
               品牌锁定组合照旧读作官网首页那一个 Lane，进门的人也知道这是后台 -->
          <span class="gate-brand-kind">控制台</span>
        </span>
      </p>

      <div class="gate-box" :class="{ 'gate-box-wide': wide }">
        <slot />
      </div>

      <p class="gate-foot"><a href="https://lane.mintpop.ai">lane.mintpop.ai</a></p>
    </main>

    <aside class="gate-panel">
      <!-- 气泡场：纯装饰，不承载信息，整层对读屏隐藏。
           16 枚是定数——CSS 按 nth-child 逐枚分配横向位置、直径、升起时长与（负）延迟，
           增减数量必须同步改 layout.css 的 gate 段，否则多出来的几枚会叠在同一条轨道上。
           负延迟让页面一打开半空中就已经有气泡在飘，不用空等一整轮。 -->
      <div class="gate-bubbles" aria-hidden="true">
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
        <span class="gate-bubble"></span>
      </div>

      <!-- 品牌块：气泡场之上的一层，整块在带子里双居中 -->
      <div class="gate-brand-block">
        <!-- 组织标记：白底方图，深墨上自成一枚印章。标尺寸避免加载前抖版。
             ⚠️ 规范站这张是 1254×1254 / 841KB 的原图，这里只用到 80px——它是气氛不是内容，
             所以降到 low 优先级并异步解码，别跟登录卡抢首屏带宽。规范站哪天出了小尺寸
             变体（如 avatar-128.png），把 src 换过去即可，这两个属性可以留着。 -->
        <img
          class="gate-avatar"
          src="https://standards.mintpop.ai/assets/brand/avatar.png"
          alt="MintPop"
          width="80"
          height="80"
          fetchpriority="low"
          decoding="async"
        />
        <!-- 品牌 slogan，英文原文不译——它是品牌语，不是界面文案。
             两层叠着演打字机：
             · 真身（.gate-slogan-real）整句始终在，只是字色透明——它撑出盒子的宽高，
               也是读屏念的、鼠标能选中复制的那一份；
             · 打字层压在真身上，只画已经打出的部分，末尾缀一枚会闪的光标，对读屏隐藏。
             分两层是为了盒子尺寸恒定：单靠一层逐字长出来，居中的句子会一边打一边左右晃。 -->
        <p class="gate-slogan">
          <span class="gate-slogan-real">{{ SLOGAN }}</span>
          <span class="gate-slogan-typed" aria-hidden="true"
            >{{ typedSlogan }}<i class="gate-caret"></i
          ></span>
        </p>
      </div>
    </aside>
  </div>
</template>
