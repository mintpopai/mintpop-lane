<script setup lang="ts">
/**
 * 状态卡：把应用里某一种状态原样摆出来，让人一眼看到「出问题时它是怎么说话的」。
 * 色调永远配一个文字标签使用，不靠颜色单独传达。
 */
import type { Tone } from "../content/copy";

defineProps<{
  label: string;
  detail: string;
  tone: Tone;
  /** 重试入口的措辞；空串表示这种状态不需要你做什么 */
  retry: string;
  /** 一句话讲清「这时候该谁动手」 */
  why: string;
}>();

/** 与 tone 配套的文字标签，保证不靠颜色单独传达 */
const toneLabel: Record<Tone, string> = {
  ok: "就绪",
  warn: "已暂停",
  danger: "要处理",
  muted: "等一下",
};
</script>

<template>
  <article :class="['card', tone]">
    <span class="badge">{{ toneLabel[tone] }}</span>

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

.badge {
  align-self: flex-start;
  margin-bottom: 14px;
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

/* 重试入口：画成应用里那个按钮的样子，但它只是展示，不可点 */
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
