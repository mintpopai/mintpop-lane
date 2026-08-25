<script setup lang="ts">
/**
 * 首屏的产品窗口示意：一个应用窗口里同时装着「浅色控制台」与「深色终端井」，
 * 这正是桌面端的真实形态——主页是浅色的链路卡 + 新建会话卡，工作区是深色终端。
 *
 * 手写矢量而非截图：随字体自适应、体积极小、改版不用重截，也不会把真实邮箱/分配号带出来。
 * 里面出现的分配号 7K3M9-QX2FT 是示例值（10 位 Crockford Base32 短码的展示形态）。
 */
import LanePathGraphic from "./LanePathGraphic.vue";
</script>

<template>
  <div class="window" role="img" aria-label="MintPop Lane 应用界面示意：链路已接入，终端中运行 Agent 会话">
    <!-- 窗口顶栏 -->
    <div class="titlebar">
      <span class="dots" aria-hidden="true"><i></i><i></i><i></i></span>
      <span class="title">MintPop Lane</span>
      <span class="status">
        <i class="led" aria-hidden="true"></i>
        已接入专属链路
      </span>
    </div>

    <div class="body">
      <!-- 会话侧栏 -->
      <div class="rail">
        <div class="rail-head mono">会话</div>
        <div class="tab on">
          <span class="tab-dot" aria-hidden="true"></span>
          <span class="tab-name mono">lane-website</span>
        </div>
        <div class="tab">
          <span class="tab-dot" aria-hidden="true"></span>
          <span class="tab-name mono">api-server</span>
        </div>
        <div class="tab add" aria-hidden="true">＋ 新建会话</div>
      </div>

      <div class="main">
        <!-- 链路卡：与桌面端主页顶上那张卡同构 -->
        <div class="card">
          <div class="card-head">
            <h3>专属链路</h3>
            <span class="chip mono">7K3M9-QX2FT</span>
          </div>
          <LanePathGraphic variant="active" :labels="false" />
          <p class="card-note">
            <strong>已接入专属链路</strong>
            <span>出口校验通过，可以启动会话。</span>
          </p>
        </div>

        <!-- 终端井 -->
        <div class="well">
          <pre class="screen mono"><span class="sigil">$</span> claude
<span class="dim">链路已就绪 · 出口校验通过</span>
欢迎回来，从哪里开始？
<span class="sigil">›</span> <span class="caret" aria-hidden="true"></span></pre>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.window {
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg);
  border: 1px solid var(--line);
  box-shadow:
    0 1px 2px rgba(11, 11, 12, 0.04),
    0 40px 80px -40px rgba(11, 11, 12, 0.28);
}

.titlebar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 16px;
  background: var(--bg-soft);
  border-bottom: 1px solid var(--line);
}

.dots {
  display: inline-flex;
  gap: 6px;
}

.dots i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d5dbd8;
}

.title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 13px;
  color: var(--ink-2);
}

.status {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  background: var(--ok-soft);
  color: var(--ok);
  font-size: 12px;
  font-weight: 500;
}

.led {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mint-deep);
  flex: none;
}

.body {
  display: flex;
  min-height: 300px;
}

/* —— 会话侧栏 —— */
.rail {
  width: 172px;
  flex: none;
  padding: 12px 10px;
  border-right: 1px solid var(--line);
  background: var(--bg-soft);
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.rail-head {
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--ink-3);
  padding: 2px 8px 8px;
}

.tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  color: var(--ink-2);
}

.tab.on {
  background: var(--bg);
  color: var(--ink);
  box-shadow: 0 1px 2px rgba(11, 11, 12, 0.05);
}

.tab-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
  background: #cfd6d3;
}

.tab.on .tab-dot {
  background: var(--mint-deep);
}

.tab-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab.add {
  margin-top: 4px;
  color: var(--ink-3);
  font-size: 12px;
}

/* —— 主区 —— */
.main {
  flex: 1;
  min-width: 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.card {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 16px 18px;
  background: linear-gradient(150deg, var(--bg-mint) 0%, var(--bg) 75%);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.card-head h3 {
  font-size: 15px;
}

.chip {
  margin-left: auto;
  font-size: 11px;
  padding: 3px 9px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--line-mint);
  background: rgba(255, 255, 255, 0.7);
  color: var(--brand-strong);
}

.card-note {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  font-size: 12.5px;
  color: var(--ink-2);
}

.card-note strong {
  color: var(--ok);
  font-weight: 600;
}

/* —— 终端井 —— */
.well {
  flex: 1;
  background: var(--well);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.screen {
  margin: 0;
  padding: 16px 18px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--well-ink);
  white-space: pre-wrap;
  overflow-x: auto;
}

.sigil {
  color: var(--mint-bright);
}

.dim {
  color: var(--well-ink-2);
}

.caret {
  display: inline-block;
  width: 7px;
  height: 14px;
  vertical-align: -3px;
  background: var(--well-ink);
}

@media (prefers-reduced-motion: no-preference) {
  .caret {
    animation: blink 1.15s step-end infinite;
  }
  @keyframes blink {
    50% {
      opacity: 0;
    }
  }
}

@media (max-width: 720px) {
  .rail {
    display: none;
  }
  .body {
    min-height: 0;
  }
}
</style>
