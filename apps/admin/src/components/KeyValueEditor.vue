<script setup lang="ts">
import type { KeyValueRow } from "../utils/nodeForm";

const props = defineProps<{ modelValue: KeyValueRow[] }>();
const emit = defineEmits<{ "update:modelValue": [KeyValueRow[]] }>();

function 更新(index: number, patch: Partial<KeyValueRow>): void {
  emit(
    "update:modelValue",
    props.modelValue.map((row, i) => (i === index ? { ...row, ...patch } : row)),
  );
}

function 新增(): void {
  emit("update:modelValue", [...props.modelValue, { key: "", value: "" }]);
}

function 删除(index: number): void {
  emit(
    "update:modelValue",
    props.modelValue.filter((_, i) => i !== index),
  );
}
</script>

<template>
  <div class="kv-editor">
    <div v-for="(row, index) in modelValue" :key="index" class="kv-editor__row">
      <input
        class="kv-editor__input"
        :value="row.key"
        placeholder="键，如 sni"
        @input="更新(index, { key: ($event.target as HTMLInputElement).value })"
      />
      <input
        class="kv-editor__input"
        :value="row.value"
        placeholder="值，true / false / 数字会按原类型下发"
        @input="更新(index, { value: ($event.target as HTMLInputElement).value })"
      />
      <button type="button" :data-test="`remove-row-${index}`" @click="删除(index)">删除</button>
    </div>
    <button type="button" data-test="add-row" @click="新增()">新增一行</button>
  </div>
</template>

<style scoped>
.kv-editor__row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.kv-editor__input {
  flex: 1;
  height: 32px;
  padding: 0 11px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font: inherit;
}
</style>
