<script setup lang="ts">
// 开关：两态、立即可读的布尔设置（如「主用机场」）。用按钮 + role="switch" 实现，
// 空格/回车切换由原生 button 负责，读屏按 aria-checked 念出开/关
defineProps<{
  modelValue: boolean;
  id?: string;
  /** 没有可见 label 关联时给屏幕阅读器用 */
  ariaLabel?: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();
</script>

<template>
  <button
    :id="id"
    type="button"
    role="switch"
    class="admin-switch"
    :class="{ on: modelValue }"
    :aria-checked="modelValue"
    :aria-label="ariaLabel"
    :disabled="disabled"
    @click="emit('update:modelValue', !modelValue)"
  >
    <span class="knob" />
  </button>
</template>

<style scoped>
.admin-switch {
  position: relative;
  flex: none;
  width: 40px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: var(--radius-pill);
  /* 关闭态要在白底上也看得出是个开关，发丝线色太淡，取次要文字色的浅调 */
  background: color-mix(in srgb, var(--color-ink-secondary) 35%, #ffffff);
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.admin-switch.on {
  background: var(--color-brand);
}

.admin-switch:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}

.admin-switch:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 20%);
  transition: transform 0.15s ease;
}

.admin-switch.on .knob {
  transform: translateX(18px);
}

@media (prefers-reduced-motion: reduce) {
  .admin-switch,
  .knob {
    transition: none;
  }
}
</style>
