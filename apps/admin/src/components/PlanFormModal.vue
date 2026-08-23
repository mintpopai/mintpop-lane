<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { CURRENCY_LABELS } from "../api/types";
import type { PlanResponse } from "../api/types";
import { showToast } from "../toast";
import { buildPlanPayload, emptyPlanForm, planToForm, validatePlanForm } from "../utils/planForm";
import Modal from "./AdminModal.vue";
import Select from "./AdminSelect.vue";

const props = defineProps<{ editing: PlanResponse | null }>();
// 弹窗由父组件 v-if 挂载/卸载，打开即初始化表单，不需要 watch 重置
const emit = defineEmits<{ close: []; saved: [] }>();

const form = ref(props.editing ? planToForm(props.editing) : emptyPlanForm());
const submitting = ref(false);

const title = computed(() => (props.editing ? `编辑套餐：${props.editing.name}` : "新建套餐"));
const currencyOptions = Object.entries(CURRENCY_LABELS).map(([value, label]) => ({
  value,
  label: `${value}（${label}）`,
}));
const enabledOptions = [
  { value: true, label: "上架" },
  { value: false, label: "停用" },
];

async function submit(): Promise<void> {
  const errors = validatePlanForm(form.value);
  if (errors.length > 0) {
    showToast("error", errors[0]);
    return;
  }
  submitting.value = true;
  try {
    const payload = buildPlanPayload(form.value);
    if (props.editing) {
      await adminApi().updatePlan(props.editing.id, payload);
    } else {
      await adminApi().createPlan(payload);
    }
    showToast("success", "已保存");
    emit("saved");
    emit("close");
  } catch (error) {
    // 410018 套餐名已存在等业务错误，服务端给的中文提示直接用
    showToast("error", error instanceof BizError ? error.message : `保存失败：${(error as Error).message}`);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Modal :title="title" @close="emit('close')">
    <div class="admin-form">
      <div class="admin-field">
        <label for="plan-name">套餐名</label>
        <input id="plan-name" v-model="form.name" class="admin-input" placeholder="如：月付套餐" />
      </div>
      <div class="admin-field">
        <label for="plan-duration">时长（天）</label>
        <input
          id="plan-duration"
          v-model.number="form.durationDays"
          class="admin-input"
          type="number"
          min="1"
          step="1"
          placeholder="如：30"
        />
      </div>
      <div class="admin-field">
        <label for="plan-price">价格</label>
        <input
          id="plan-price"
          v-model.number="form.price"
          class="admin-input"
          type="number"
          min="0"
          step="0.01"
          placeholder="如：29.90"
        />
      </div>
      <div class="admin-field">
        <label for="plan-currency">币种</label>
        <Select id="plan-currency" v-model="form.currency" :options="currencyOptions" aria-label="币种" />
      </div>
      <div class="admin-field">
        <label for="plan-enabled">状态</label>
        <Select id="plan-enabled" v-model="form.enabled" :options="enabledOptions" aria-label="状态" />
      </div>
      <div class="admin-field">
        <label for="plan-remark">备注</label>
        <input id="plan-remark" v-model="form.remark" class="admin-input" placeholder="管理员自用说明，可空" />
      </div>
    </div>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">取消</button>
      <button type="button" class="admin-btn" :disabled="submitting" @click="submit()">
        {{ submitting ? "保存中…" : "保存" }}
      </button>
    </template>
  </Modal>
</template>
