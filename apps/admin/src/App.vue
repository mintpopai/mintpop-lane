<script setup lang="ts">
// 顶层只做两件事：路由出口 + 全局 toast（覆盖 gate 页与后台两种形态）
import { toast } from "./toast";
</script>

<template>
  <router-view />

  <Transition name="toast">
    <div v-if="toast" class="toast" :class="toast.type" role="status">
      {{ toast.text }}
    </div>
  </Transition>
</template>

<style scoped>
.toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  border-radius: var(--radius-card);
  background: var(--counter-deep);
  color: #ffffff;
  font-size: 14px;
  box-shadow: 0 10px 30px rgba(15, 26, 22, 0.28);
  z-index: 40;
}

/* 成功用品牌绿，但绿只做底、字换深墨——沿用主按钮那对经过校验的配色（9.1:1） */
.toast.success {
  background: var(--color-brand);
  color: var(--counter-deep);
}

.toast.error {
  background: var(--counter-danger);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}
</style>
