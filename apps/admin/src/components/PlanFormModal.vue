<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS, CURRENCY_LABELS } from "../api/types";
import type { PlanResponse } from "../api/types";
import { showToast } from "../toast";
import { buildPlanPayload, emptyPlanForm, planToForm, validatePlanForm } from "../utils/planForm";
import Modal from "./AdminModal.vue";
import Select from "./AdminSelect.vue";
import ImageUploadButton from "./ImageUploadButton.vue";
import RichTextEditor from "./RichTextEditor.vue";

const props = defineProps<{ editing: PlanResponse | null }>();
// 弹窗由父组件 v-if 挂载/卸载，打开即初始化表单，不需要 watch 重置
const emit = defineEmits<{ close: []; saved: [] }>();

const form = ref(props.editing ? planToForm(props.editing) : emptyPlanForm());
const submitting = ref(false);
/** 图片地址取不到图时，预览位给一句人话，而不是一个碎图标 */
const imageError = ref(false);

// 换了地址就重新试一次，别把上一张的失败状态留在新地址上
watch(
  () => form.value.imageUrl,
  () => {
    imageError.value = false;
  },
);

const title = computed(() => (props.editing ? `编辑套餐：${props.editing.name}` : "新建套餐"));
const agentOptions = Object.entries(AGENT_TYPE_LABELS).map(([value, label]) => ({ value, label }));
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
    showToast(
      "error",
      error instanceof BizError ? error.message : `保存失败：${(error as Error).message}`,
    );
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Modal :title="title" wide flush @close="emit('close')">
    <form class="plan-editor" @submit.prevent>
      <section class="copy-pane">
        <div class="admin-field">
          <label for="plan-name">套餐名</label>
          <input
            id="plan-name"
            v-model="form.name"
            class="admin-input"
            placeholder="如：月付套餐"
          />
        </div>
        <div class="admin-field">
          <label for="plan-description">
            描述
            <span class="char-count" :class="{ over: form.description.length > 255 }">
              {{ form.description.length }} / 255
            </span>
          </label>
          <textarea
            id="plan-description"
            v-model="form.description"
            class="admin-input"
            rows="2"
            placeholder="如：含 5 个并发席位，不限流量"
          ></textarea>
          <p class="field-note">控制台购买卡片上，套餐名下面那行小字。</p>
        </div>
        <div class="admin-field detail-field">
          <label for="plan-detail">套餐详情</label>
          <RichTextEditor id="plan-detail" v-model="form.detail" fill />
          <p class="field-note">控制台点「详情」时展开的长文案，留空则不显示「详情」入口。</p>
        </div>
      </section>

      <aside class="tag-pane">
        <div class="admin-field">
          <label for="plan-agent">Agent 类型</label>
          <Select
            id="plan-agent"
            v-model="form.agentType"
            :options="agentOptions"
            aria-label="Agent 类型"
          />
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
          <Select
            id="plan-currency"
            v-model="form.currency"
            :options="currencyOptions"
            aria-label="币种"
          />
        </div>
        <div class="admin-field">
          <label for="plan-enabled">状态</label>
          <Select
            id="plan-enabled"
            v-model="form.enabled"
            :options="enabledOptions"
            aria-label="状态"
          />
        </div>
        <div class="admin-field">
          <label for="plan-image">套餐图</label>
          <!-- 手填地址与本地上传两条路都留着：上传成功直接回填地址，预览随 watch 刷新 -->
          <div class="image-row">
            <input
              id="plan-image"
              v-model="form.imageUrl"
              class="admin-input"
              type="url"
              placeholder="https://…"
            />
            <ImageUploadButton @uploaded="form.imageUrl = $event" />
          </div>
          <div class="image-preview">
            <img
              v-if="form.imageUrl && !imageError"
              :src="form.imageUrl"
              alt=""
              @error="imageError = true"
            />
            <p v-else class="image-note">
              {{ imageError ? "这个地址取不到图片。" : "填了地址或上传图片就能在这里看到效果。" }}
            </p>
          </div>
        </div>
        <div class="admin-field">
          <label for="plan-remark">备注</label>
          <input
            id="plan-remark"
            v-model="form.remark"
            class="admin-input"
            placeholder="管理员自用说明，可空"
          />
        </div>
      </aside>
    </form>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">取消</button>
      <button type="button" class="admin-btn" :disabled="submitting" @click="submit()">
        {{ submitting ? "保存中…" : "保存" }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
/* 左右两栏：左边文案要给富文本留出高度，右边参数栏固定宽 */
.plan-editor {
  display: flex;
  align-items: stretch;
  min-height: 420px;
}

.copy-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

/* 详情吃掉左栏剩余高度，编辑器才撑得开 */
.detail-field {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.tag-pane {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background: var(--color-bg-cloud);
  border-left: 1px solid var(--color-border);
  overflow-y: auto;
}

/* 窄屏落成上下两段，参数栏的左边框换成上边框 */
@media (max-width: 900px) {
  .plan-editor {
    flex-direction: column;
  }

  .tag-pane {
    width: auto;
    border-left: none;
    border-top: 1px solid var(--color-border);
  }
}

/* 字数计数贴在 label 右侧，超限标红 */
.char-count {
  float: right;
  font-family: var(--font-fact);
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.char-count.over {
  color: var(--counter-danger);
}

/* 字段下面的一句解释，说清这个值会出现在哪 */
.field-note {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.image-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.image-preview {
  margin-top: 8px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.image-preview img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.image-note {
  font-size: 13px;
  color: var(--color-ink-secondary);
  padding: 0 12px;
  text-align: center;
}
</style>
