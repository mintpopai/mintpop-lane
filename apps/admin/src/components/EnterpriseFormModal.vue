<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type { EnterpriseResponse } from "../api/types";
import { showToast } from "../toast";
import {
  buildEnterprisePayload,
  emptyEnterpriseForm,
  enterpriseToForm,
  toggleAgentType,
  validateEnterpriseForm,
} from "../utils/enterpriseForm";
import Modal from "./AdminModal.vue";
import Select from "./AdminSelect.vue";

const props = defineProps<{ editing: EnterpriseResponse | null }>();
// 弹窗由父组件 v-if 挂载/卸载，打开即初始化表单，不需要 watch 重置
const emit = defineEmits<{ close: []; saved: [] }>();

const form = ref(props.editing ? enterpriseToForm(props.editing) : emptyEnterpriseForm());
const submitting = ref(false);

const title = computed(() => (props.editing ? `编辑企业：${props.editing.name}` : "新建企业"));
const enabledOptions = [
  { value: true, label: "启用" },
  { value: false, label: "停用" },
];

/**
 * 勾选项以 AGENT_TYPE_LABELS 为准，再补上表单里出现过、本前端却不认识的类型
 * （服务端新增了类型时，编辑老记录不该把它悄悄丢掉）。
 */
const agentChoices = computed(() => {
  const known = Object.entries(AGENT_TYPE_LABELS).map(([value, label]) => ({ value, label }));
  const unknown = form.value.agentTypes
    .filter((type) => !(type in AGENT_TYPE_LABELS))
    .map((type) => ({ value: type, label: type }));
  return [...known, ...unknown];
});

async function submit(): Promise<void> {
  const errors = validateEnterpriseForm(form.value);
  if (errors.length > 0) {
    showToast("error", errors[0]);
    return;
  }
  submitting.value = true;
  try {
    const payload = buildEnterprisePayload(form.value);
    if (props.editing) {
      await adminApi().updateEnterprise(props.editing.id, payload);
    } else {
      await adminApi().createEnterprise(payload);
    }
    showToast("success", "已保存");
    emit("saved");
    emit("close");
  } catch (error) {
    // 410021 企业名称已存在、410022 域名已存在等业务错误，服务端给的中文提示直接用
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
        <label for="enterprise-name">企业名称</label>
        <input id="enterprise-name" v-model="form.name" class="admin-input" placeholder="如：Acme 科技" />
      </div>
      <div class="admin-field">
        <label for="enterprise-domain">企业域名</label>
        <input
          id="enterprise-domain"
          v-model="form.domain"
          class="admin-input"
          placeholder="如：acme.com"
        />
        <p class="admin-note">只填裸域名，不带 https:// 与路径；保存时统一转小写。</p>
      </div>
      <div class="admin-field">
        <span id="enterprise-agent-types" class="field-label">支持的 Agent 类型</span>
        <!-- 多选用 chip 切换，不用原生 checkbox：这套后台的控件全是自绘的，
             系统方框在里面格格不入。aria-pressed 承载勾选状态 -->
        <div class="agent-choices" role="group" aria-labelledby="enterprise-agent-types">
          <button
            v-for="choice in agentChoices"
            :key="choice.value"
            type="button"
            class="admin-chip agent-chip"
            :class="{ selected: form.agentTypes.includes(choice.value) }"
            :aria-pressed="form.agentTypes.includes(choice.value)"
            @click="toggleAgentType(form, choice.value)"
          >
            <span v-if="form.agentTypes.includes(choice.value)" class="agent-chip-check" aria-hidden="true">
              ✓
            </span>
            {{ choice.label }}
          </button>
        </div>
        <p class="admin-note">分配订阅时，只有这里勾中的类型能归属到本企业。</p>
      </div>
      <div class="admin-field">
        <label for="enterprise-enabled">状态</label>
        <Select
          id="enterprise-enabled"
          v-model="form.enabled"
          :options="enabledOptions"
          aria-label="状态"
        />
      </div>
      <div class="admin-field">
        <label for="enterprise-remark">备注</label>
        <input
          id="enterprise-remark"
          v-model="form.remark"
          class="admin-input"
          placeholder="管理员自用说明，可空"
        />
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

<style scoped>
/* 多选没有单一 for 目标，label 换成 span，但字号颜色要与其它字段的 label 一致 */
.field-label {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

.agent-choices {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 勾号只在选中时出现，不给它留常驻空位——留了空位未选中的 chip 左边就空一块、
   文字也不居中，比「勾选时 chip 变宽一点」难看得多 */
.agent-chip-check {
  color: var(--color-brand-deep);
  font-size: 12px;
}

/* 选中态沿用下拉面板里「已选项」的语言：薄荷底 + 勾号。
   不用筛选 chip 的深墨底——那表达「我在看什么」，这里表达「这个值选上了」 */
.agent-chip.selected {
  border-color: color-mix(in srgb, var(--color-brand) 55%, #ffffff);
  background: color-mix(in srgb, var(--color-brand) 14%, #ffffff);
  color: var(--color-ink);
  font-weight: 600;
}

.agent-chip.selected .agent-chip-check {
  font-weight: 400;
}
</style>
