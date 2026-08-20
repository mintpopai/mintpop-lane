<script setup lang="ts">
// 弹窗的键盘行为：aria-modal 只是个声明，真正让它成立的是「焦点进得来、出不去、关得掉」。
// 缺这三件事时键盘用户会在被遮住的页面里继续 Tab，等于弹窗根本没拦住他。
import { onBeforeUnmount, onMounted, ref } from "vue";

defineProps<{
  title: string;
  /** 宽弹窗（订阅管理这类「列表 + 表单」内容）用 wide */
  wide?: boolean;
}>();
const emit = defineEmits<{ close: [] }>();

const dialog = ref<HTMLElement | null>(null);
/** 打开弹窗前焦点在哪，关闭后还回去，不把人丢回页首 */
let restoreTo: HTMLElement | null = null;

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function focusables(): HTMLElement[] {
  return Array.from(dialog.value?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []);
}

onMounted(() => {
  restoreTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  // 落在第一个可聚焦元素（通常是关闭按钮）上，而不是危险操作上
  (focusables()[0] ?? dialog.value)?.focus();
});

onBeforeUnmount(() => restoreTo?.focus());

/** Esc 关闭；Tab 在弹窗内首尾相接地循环，不跑到背后的页面去 */
function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    emit("close");
    return;
  }
  if (event.key !== "Tab") {
    return;
  }
  const items = focusables();
  if (items.length === 0) {
    return;
  }
  const first = items[0];
  const last = items[items.length - 1];
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.value)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
</script>

<template>
  <Teleport to="body">
    <!-- 遮罩不响应点击：弹窗里多是填了一半的表单，误点空白处关掉会丢数据，只留 Esc 与关闭按钮 -->
    <div class="overlay">
      <!-- tabindex="-1"：弹窗里万一没有可聚焦元素时，焦点也有地方落，键盘事件才收得到 -->
      <div
        ref="dialog"
        class="dialog"
        :class="{ wide }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        tabindex="-1"
        @keydown="onKeydown"
      >
        <header class="head">
          <h3 class="head-title">{{ title }}</h3>
          <button type="button" class="close" aria-label="关闭" @click="emit('close')">×</button>
        </header>
        <div class="content">
          <slot />
        </div>
        <footer class="foot">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 26, 22, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 30;
}

.dialog {
  width: 100%;
  max-width: 560px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  border-radius: var(--radius-card);
  box-shadow: 0 18px 56px rgba(15, 26, 22, 0.28);
}

.dialog.wide {
  max-width: 860px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.head-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
}

.close {
  border: none;
  background: none;
  font-size: 22px;
  line-height: 1;
  color: var(--color-ink-secondary);
  cursor: pointer;
}

.close:hover {
  color: var(--color-ink);
}

.content {
  padding: 20px;
  overflow-y: auto;
}

.foot {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--color-border);
}

/* 没有 footer 内容时别留一条空白横条 */
.foot:empty {
  display: none;
}
</style>
