<script setup lang="ts">
// 闸门页（登录引导 / 无权限 / 登录错误）共用的分屏骨架：左边深墨品牌面板讲「这是什么」，
// 右边白底由调用方放标题、说明与登录动作。与 meetu / mintpop-shop 管理端同一版式，
// 移植时只换左面板的文案与脚注。样式在 styles/layout.css 的 gate 段。
</script>

<template>
  <div class="gate">
    <aside class="gate-panel">
      <p class="gate-kicker">Lane 管理后台</p>
      <div class="gate-story">
        <h2 class="gate-tagline">线路先通，<br />其余再谈。</h2>
        <span class="gate-stroke" aria-hidden="true"></span>
        <p class="gate-tagline-sub">谁能上、走哪条线、用哪张凭据，都在这里定。</p>
      </div>
      <p class="gate-foot"><a href="https://lane.mintpop.ai">lane.mintpop.ai</a></p>
    </aside>

    <main class="gate-main">
      <div class="gate-box">
        <p class="gate-brand">
          <img
            class="gate-icon"
            src="https://standards.mintpop.ai/assets/products/lane/lane-app-cloud.png"
            alt=""
          />
          <span class="gate-brand-text">
            <img
              class="gate-wordmark"
              src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-dark.png"
              alt="MintPop"
            />
            Lane 管理后台
          </span>
        </p>
        <slot />
      </div>
    </main>
  </div>
</template>
