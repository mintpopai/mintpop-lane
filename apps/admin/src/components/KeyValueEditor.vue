<script setup lang="ts">
import type { KeyValueRow } from "../utils/nodeForm";

const props = defineProps<{ modelValue: KeyValueRow[] }>();
const emit = defineEmits<{ "update:modelValue": [KeyValueRow[]] }>();

function update(index: number, patch: Partial<KeyValueRow>): void {
  emit(
    "update:modelValue",
    props.modelValue.map((row, i) => (i === index ? { ...row, ...patch } : row)),
  );
}

function add(): void {
  emit("update:modelValue", [...props.modelValue, { key: "", value: "" }]);
}

function remove(index: number): void {
  emit(
    "update:modelValue",
    props.modelValue.filter((_, i) => i !== index),
  );
}
</script>

<template>
  <div class="kv-editor">
    <div v-for="(row, index) in modelValue" :key="index" class="kv-row">
      <!-- 键是配置名（sni 这类系统事实），走等宽 -->
      <input
        class="admin-input fact kv-input"
        :value="row.key"
        placeholder="键，如 sni"
        @input="update(index, { key: ($event.target as HTMLInputElement).value })"
      />
      <input
        class="admin-input kv-input"
        :value="row.value"
        placeholder="值，true / false / 数字会按原类型下发"
        @input="update(index, { value: ($event.target as HTMLInputElement).value })"
      />
      <button
        type="button"
        class="admin-btn-ghost"
        :data-test="`remove-row-${index}`"
        @click="remove(index)"
      >
        删除
      </button>
    </div>
    <button type="button" class="admin-btn-ghost" data-test="add-row" @click="add()">新增一行</button>
  </div>
</template>

<style scoped>
.kv-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.kv-input {
  flex: 1;
  min-width: 0;
}
</style>
