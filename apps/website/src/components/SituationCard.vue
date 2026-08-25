<script setup lang="ts">
/**
 * 处境卡：把桌面端某一种链路处境原样搬到页面上。
 *
 * label / detail / tone / 重试按钮措辞都取自桌面端 `src/link.ts` 的 present 表（见 content/copy.ts），
 * 不在这里另编一套说法——展示的就是用户真正会看到的那句话。
 * 色调永远配一个文字标签使用，不靠颜色单独传达状态。
 */
import type { Tone } from "../content/copy";

defineProps<{
  code: string;
  label: string;
  detail: string;
  tone: Tone;
  /** 重试入口的措辞；空串表示该处境没有重试按钮 */
  retry: string;
  /** 一句话讲清「为什么这一格是这个色调」 */
  why: string;
}>();

/** 与 tone 配套的文字标签，保证不靠颜色单独传达 */
const toneLabel: Record<Tone, string> = {
  ok: "就绪",
  warn: "已暂停",
  danger: "故障",
  muted: "等待中",
};
</script>

<template>
  <article :class="['card', tone]">
    <header class="head">
      <span class="badge">{{ toneLabel[tone] }}</span>
      <code class="code mono">{{ code }}</code>
    </header>

    <h3 class="label">{{ label }}</h3>
    <p class="detail">{{ detail }}</p>

    <div class="foot">
      <span v-if="retry" class="retry">{{ retry }}</span>
      <span v-else class="retry ghost">无需操作</span>
      <p class="why">{{ why }}</p>
    </div>
  </article>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 22px 22px 20px;
  background: var(--bg);
  border-left-width: 3px;
}

.card.ok {
  border-left-color: var(--mint-deep);
}
.card.warn {
  border-left-color: var(--warn);
}
.card.danger {
  border-left-color: var(--danger);
}
.card.muted {
  border-left-color: var(--ink-3);
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.badge {
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
}

.card.ok .badge {
  background: var(--ok-soft);
  color: var(--ok);
}
.card.warn .badge {
  background: var(--warn-soft);
  color: var(--warn);
}
.card.danger .badge {
  background: var(--danger-soft);
  color: var(--danger);
}
.card.muted .badge {
  background: var(--muted-soft);
  color: var(--ink-2);
}

.code {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-3);
  letter-spacing: 0.02em;
}

.label {
  font-size: 19px;
}

.detail {
  margin-top: 8px;
  font-size: 14.5px;
  color: var(--ink-2);
}

.foot {
  margin-top: auto;
  padding-top: 18px;
}

/* 重试入口：画成产品里那个按钮的样子，但它只是展示，不可点 */
.retry {
  display: inline-block;
  padding: 7px 15px;
  border-radius: var(--radius-button);
  border: 1px solid var(--line);
  font-size: 13px;
  font-weight: 500;
  color: var(--ink);
  background: var(--bg-soft);
}

.retry.ghost {
  border-style: dashed;
  color: var(--ink-3);
  background: transparent;
}

.why {
  margin-top: 12px;
  font-size: 13px;
  color: var(--ink-3);
}
</style>
