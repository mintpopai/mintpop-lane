<script setup lang="ts">
// 闸门页（登录引导 / 无权限 / 登录错误）共用的骨架。主次按「登录卡是主角」排：
//
// · 主区占七成多，Cloud 底，登录卡浮在正中，内容居中对齐——页面上唯一的焦点。
// · 左上角 chrome 讲产品：只写「Lane 管理后台」。产品图标改由登录卡当视觉主体（见
//   LoginView 的 .gate-mark），左上角再挂一枚小的就是同一张图一大一小说两遍。
//   站点地址退到左下角，两处都是灰色小字，成对。
// · 右侧一条窄的深墨带讲母品牌：一组「车道」横条 + MintPop 组织标记 + slogan，
//   照飞书那条带的做法**整块在带子里水平垂直双居中**、文字也居中对齐，
//   上下各留出约五分之一的空。产品就叫 Lane，那组线是它的具象；
//   线自己说不清是什么，所以下面必须有品牌在场。
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
      <!-- 纯图形，不承载信息，整组对读屏隐藏 -->
      <div class="gate-lanes" aria-hidden="true">
        <span class="gate-lane"></span>
        <span class="gate-lane"></span>
        <span class="gate-lane"></span>
        <span class="gate-lane"></span>
        <span class="gate-lane gate-lane-live"></span>
        <span class="gate-lane"></span>
        <span class="gate-lane"></span>
        <span class="gate-lane"></span>
        <span class="gate-lane"></span>
      </div>

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
    </aside>
  </div>
</template>
