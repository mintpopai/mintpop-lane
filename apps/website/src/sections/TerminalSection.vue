<script setup lang="ts">
import TerminalMock from "../components/TerminalMock.vue";
import { useI18n } from "../i18n";

const { t } = useI18n();

/** 终端 mock 侧栏里的示例会话，与首屏窗口里那两个保持一致 */
const tabs = [
  { name: "lane-website", active: true },
  { name: "api-server" },
  { name: "docs" },
] as const;
</script>

<template>
  <!-- 全站唯一一块深色井：让它成为视觉重音，不在别处重复使用 -->
  <section id="terminal" class="terminal-section">
    <div class="container">
      <div class="head">
        <p class="kicker">{{ t.terminal.kicker }}</p>
        <h2 class="title">{{ t.terminal.title }}</h2>
        <p class="lede">{{ t.terminal.lede }}</p>
      </div>

      <div class="split">
        <TerminalMock :tabs="tabs" />

        <ul class="points">
          <li v-for="p in t.terminal.points" :key="p.title">
            <h3>{{ p.title }}</h3>
            <p>{{ p.body }}</p>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.terminal-section {
  padding: 112px 0;
  background: var(--well);
  color: var(--well-ink);
}

.head {
  max-width: 44em;
  margin-bottom: 52px;
}

/* 深色底上的 kicker 与强调色改用 --mint-bright：#0A8265 在 #101614 上读不出来 */
.head .kicker {
  color: var(--mint-bright);
}

.title {
  font-size: clamp(28px, 4vw, 42px);
  color: #ffffff;
}

.lede {
  margin-top: 16px;
  font-size: 17px;
  color: var(--well-ink-2);
}

.split {
  display: grid;
  grid-template-columns: 1.25fr 1fr;
  gap: 40px;
  align-items: start;
}

.points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 26px;
}

.points li {
  padding-left: 18px;
  border-left: 2px solid rgba(31, 227, 173, 0.35);
}

.points h3 {
  font-size: 17px;
  color: #ffffff;
  margin-bottom: 7px;
}

.points p {
  font-size: 14.5px;
  color: var(--well-ink-2);
}

@media (max-width: 900px) {
  .terminal-section {
    padding: 80px 0;
  }
  .split {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}
</style>
