<script setup lang="ts">
/**
 * 深色终端块。底色用桌面端的 --well（#101614），官网与产品共用同一块深色，
 * 让「浅色控制台里嵌一口深色井」这个对比在官网上也成立。
 *
 * 内容是手写的高保真示意，不是截图：随主题与字体自适应、体积极小、改版不用重截。
 */
import { terminal } from "../content/copy";

withDefaults(
  defineProps<{
    /** 侧栏会话标签；传空数组则不渲染侧栏 */
    tabs?: readonly { name: string; active?: boolean }[];
    /** 是否显示 macOS 风格窗口交通灯 */
    chrome?: boolean;
  }>(),
  { tabs: () => [], chrome: true },
);

const lines = terminal.lines;
</script>

<template>
  <div class="well" role="img" aria-label="内置终端示意：链路已接通，正在运行 Agent 会话">
    <div v-if="chrome" class="bar">
      <span class="dots" aria-hidden="true"><i></i><i></i><i></i></span>
      <span class="bar-title mono">MintPop Lane</span>
      <span class="state">
        <i class="led" aria-hidden="true"></i>
        链路正常
      </span>
    </div>

    <div class="body">
      <ul v-if="tabs.length" class="rail">
        <li v-for="t in tabs" :key="t.name" :class="['tab', { on: t.active }]">
          <span class="tab-dot" aria-hidden="true"></span>
          <span class="tab-name mono">{{ t.name }}</span>
        </li>
      </ul>

      <pre class="screen mono"><template v-for="(l, i) in lines" :key="i"><span
          v-if="l.kind === 'prompt'"
        ><span class="sigil">$</span> {{ l.text }}
</span><span v-else-if="l.kind === 'dim'" class="dim">{{ l.text }}
</span><span v-else-if="l.kind === 'out'">{{ l.text }}
</span><span v-else class="caret-line"><span class="sigil">›</span> <span
            class="caret"
            aria-hidden="true"
          ></span></span></template></pre>
    </div>
  </div>
</template>

<style scoped>
.well {
  background: var(--well);
  border-radius: var(--radius);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 30px 60px -30px rgba(11, 11, 12, 0.5);
}

.bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--well-2);
  border-bottom: 1px solid var(--well-line);
}

.dots {
  display: inline-flex;
  gap: 6px;
}

.dots i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(215, 224, 220, 0.22);
}

.bar-title {
  font-size: 12px;
  color: var(--well-ink-2);
}

.state {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--mint-bright);
}

.led {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mint-bright);
}

.body {
  display: flex;
  min-height: 200px;
}

.rail {
  list-style: none;
  margin: 0;
  padding: 10px 8px;
  width: 168px;
  flex: none;
  border-right: 1px solid var(--well-line);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  border-radius: 7px;
  font-size: 12px;
  color: var(--well-ink-2);
}

.tab.on {
  background: rgba(31, 227, 173, 0.1);
  color: var(--well-ink);
}

.tab-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
  background: rgba(215, 224, 220, 0.3);
}

.tab.on .tab-dot {
  background: var(--mint-bright);
}

.tab-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.screen {
  flex: 1;
  margin: 0;
  padding: 18px 20px;
  font-size: 13px;
  line-height: 1.75;
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

.caret-line {
  display: inline-flex;
  align-items: center;
}

.caret {
  display: inline-block;
  width: 8px;
  height: 15px;
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

@media (max-width: 640px) {
  .rail {
    display: none;
  }
}
</style>
