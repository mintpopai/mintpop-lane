<script setup lang="ts">
// 危险操作确认弹窗（替代 ElMessageBox.confirm）：红色主按钮只在这里出现，页面上不摆
import Modal from "./AdminModal.vue";

defineProps<{
  title: string;
  message: string;
  /** 确认按钮文案，默认「删除」 */
  confirmText?: string;
  /** 提交中禁用按钮，防止连点重复删除 */
  busy?: boolean;
  /** 只禁用确认按钮（取消仍可点），用于前置条件不满足、不允许继续的场景 */
  confirmDisabled?: boolean;
}>();
const emit = defineEmits<{ confirm: []; cancel: [] }>();
</script>

<template>
  <Modal :title="title" @close="emit('cancel')">
    <p class="message">{{ message }}</p>
    <!-- 确认前要多选一项时（如重算方式）放这里，排在说明之下 -->
    <slot />
    <template #footer>
      <button type="button" class="admin-btn-ghost" :disabled="busy" @click="emit('cancel')">
        取消
      </button>
      <button
        type="button"
        class="admin-btn danger"
        :disabled="busy || confirmDisabled"
        @click="emit('confirm')"
      >
        {{ confirmText ?? "删除" }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.message {
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-ink);
}
</style>
