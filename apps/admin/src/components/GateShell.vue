<script setup lang="ts">
// 闸门页（登录引导 / 无权限 / 登录错误）共用的骨架。主次按「登录卡是主角」排：
//
// · 主区占七成多，Cloud 底，登录卡浮在正中，内容居中对齐——页面上唯一的焦点。
// · 左上角 chrome 讲产品：只写「Lane 管理后台」。产品图标改由登录卡当视觉主体（见
//   LoginView 的 .gate-mark），左上角再挂一枚小的就是同一张图一大一小说两遍。
//   站点地址退到左下角，两处都是灰色小字，成对。
// · 右侧一条窄的深墨带讲母品牌：一层气泡场 + MintPop 组织标记 + slogan。
//   气泡场是整条带子的背景层（铺满、绝对定位），品牌块压在它上层、在带子里双居中——
//   气泡从标记与 slogan 身后升上来、在顶部啵地破开，整条带读起来是一杯汽水，
//   而不是「一块图形 + 一块文字」。它演的是品牌语本身：Pop（气泡破裂）into
//   something fresh（薄荷、汽水）。气泡只是气氛，说不清自己是什么，所以品牌必须在场。
//
// 两边分工不交叉：左上角只出现 Lane，带子里只出现 MintPop，同一个标识不说两遍
//（组织标记那张图本身就是 mintpop 字样，所以左上角不再挂横版词标）。
// 闸门页只回答「这是哪」与「怎么进」，产品能干什么属于官网，不在门口讲。
// 样式在 styles/layout.css 的 gate 段。

defineProps<{
  /** 卡片放宽一档。给正文较长的页用（排障说明），窄卡会把中文断得很碎 */
  wide?: boolean;
}>();
</script>

<template>
  <div class="gate">
    <main class="gate-main">
      <p class="gate-brand"><span class="gate-brand-text">Lane 管理后台</span></p>

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
             ⚠️ 规范站这张是 1254×1254 / 841KB 的原图，这里只用到 64px——它是气氛不是内容，
             所以降到 low 优先级并异步解码，别跟登录卡抢首屏带宽。规范站哪天出了小尺寸
             变体（如 avatar-128.png），把 src 换过去即可，这两个属性可以留着。 -->
        <img
          class="gate-avatar"
          src="https://standards.mintpop.ai/assets/brand/avatar.png"
          alt="MintPop"
          width="64"
          height="64"
          fetchpriority="low"
          decoding="async"
        />
        <!-- 品牌 slogan，英文原文不译——它是品牌语，不是界面文案 -->
        <p class="gate-slogan">Pop into something fresh</p>
      </div>
    </aside>
  </div>
</template>
