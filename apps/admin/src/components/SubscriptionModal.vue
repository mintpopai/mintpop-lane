<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type { AdminSubscriptionResponse, AdminUserResponse, PlanResponse } from "../api/types";
import { showToast } from "../toast";
import { fromDatetimeLocal, toDatetimeLocal } from "../utils/datetimeLocal";
import { formatDateTime } from "../utils/format";
import {
  buildSubscriptionCreatePayload,
  buildSubscriptionUpdatePayload,
  computeEndsAt,
  emptySubscriptionForm,
  formatPlanLabel,
  subscriptionToForm,
  validateSubscriptionForm,
  type SubscriptionFormModel,
} from "../utils/subscriptionForm";
import ConfirmDialog from "./ConfirmDialog.vue";
import Modal from "./AdminModal.vue";
import Select from "./AdminSelect.vue";

const props = defineProps<{ user: AdminUserResponse }>();
const emit = defineEmits<{ close: []; changed: [] }>();

const list = ref<AdminSubscriptionResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const formMode = ref<"hidden" | "create" | "edit">("hidden");
const form = ref<SubscriptionFormModel>(emptySubscriptionForm());
/** 编辑中的原始行：套餐快照（名称/时长）从这里取，不进表单模型 */
const editingRow = ref<AdminSubscriptionResponse | null>(null);
const plans = ref<PlanResponse[]>([]);
const submitting = ref(false);
const pendingDelete = ref<AdminSubscriptionResponse | null>(null);
const deleting = ref(false);

/** 管理员当前浏览器时区，标在表单里免得填的人心里没数 */
const localTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

/** 只列上架套餐——分配只能从可售卖的选项里挑 */
const planOptions = computed(() =>
  plans.value.filter((plan) => plan.enabled).map((plan) => ({ value: plan.id, label: formatPlanLabel(plan) })),
);

function agentLabel(agentType: string): string {
  return AGENT_TYPE_LABELS[agentType as keyof typeof AGENT_TYPE_LABELS] ?? agentType;
}

/** agent 类型不可选：新增随所选套餐确定，编辑展示分配时的快照 */
const agentTypeDisplay = computed<string>(() => {
  if (formMode.value === "edit") {
    return agentLabel(editingRow.value?.agentType ?? "");
  }
  const selected = plans.value.find((plan) => plan.id === form.value.planId);
  return selected ? agentLabel(selected.agentType) : "选套餐后自动确定";
});

/** 止期推算用的时长：新增取所选套餐，编辑取分配时的快照 */
const durationDays = computed<number | null>(() => {
  if (formMode.value === "edit") {
    return editingRow.value?.planDurationDays ?? null;
  }
  return plans.value.find((plan) => plan.id === form.value.planId)?.durationDays ?? null;
});

/** 预计止期 = 起期 + 套餐时长，只读展示；真正落库的值由服务端算 */
const predictedEndsAt = computed<string | null>(() => {
  if (!form.value.startsAt || durationDays.value === null) {
    return null;
  }
  return formatDateTime(computeEndsAt(form.value.startsAt, durationDays.value).toISOString());
});

function reportError(error: unknown, prefix: string): void {
  showToast("error", error instanceof BizError ? error.message : `${prefix}：${(error as Error).message}`);
}

async function loadList(): Promise<void> {
  loading.value = true;
  try {
    list.value = await adminApi().listSubscriptions(props.user.id);
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

async function loadPlans(): Promise<void> {
  try {
    plans.value = await adminApi().listPlans();
  } catch (error) {
    reportError(error, "套餐加载失败");
  }
}

onMounted(() => {
  void loadList();
  void loadPlans();
});

function create(): void {
  form.value = emptySubscriptionForm();
  editingRow.value = null;
  formMode.value = "create";
}

function edit(subscription: AdminSubscriptionResponse): void {
  form.value = subscriptionToForm(subscription);
  editingRow.value = subscription;
  formMode.value = "edit";
}

async function submit(): Promise<void> {
  const mode = formMode.value === "edit" ? "edit" : "create";
  const errors = validateSubscriptionForm(form.value, mode);
  if (errors.length > 0) {
    showToast("error", errors[0]);
    return;
  }

  submitting.value = true;
  try {
    if (mode === "edit") {
      await adminApi().updateSubscription(form.value.id as number, buildSubscriptionUpdatePayload(form.value));
    } else {
      await adminApi().createSubscription(props.user.id, buildSubscriptionCreatePayload(form.value));
    }
    showToast("success", "已保存");
    formMode.value = "hidden";
    await loadList();
    emit("changed");
  } catch (error) {
    reportError(error, "保存失败");
  } finally {
    submitting.value = false;
  }
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deleteSubscription(pendingDelete.value.id);
    showToast("success", "已删除");
    pendingDelete.value = null;
    await loadList();
    emit("changed");
  } catch (error) {
    reportError(error, "删除失败");
  } finally {
    deleting.value = false;
  }
}
</script>

<template>
  <Modal :title="`订阅管理：${user.name}`" wide @close="emit('close')">
    <div class="admin-toolbar">
      <span class="spacer" />
      <button type="button" class="admin-btn" @click="create()">分配订阅</button>
    </div>

    <p v-if="loading" class="admin-hint">加载中……</p>
    <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>
    <div v-else class="admin-card">
      <p v-if="list.length === 0" class="admin-hint">还没有订阅，点右上角「分配订阅」从套餐里选一个。</p>
      <table v-else class="admin-table dense">
        <thead>
          <tr>
            <th>分配号</th>
            <th>套餐</th>
            <th>Agent</th>
            <th>起期</th>
            <th>止期</th>
            <th>凭据</th>
            <th>备注</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td class="fact muted assignment-no" :title="row.assignmentNo">{{ row.assignmentNo }}</td>
            <td>
              {{ row.name }}
              <span class="muted">（{{ row.planDurationDays }} 天 · {{ row.planPrice }} {{ row.planCurrency }}）</span>
            </td>
            <td>{{ AGENT_TYPE_LABELS[row.agentType as keyof typeof AGENT_TYPE_LABELS] ?? row.agentType }}</td>
            <td class="fact muted">{{ formatDateTime(row.startsAt) }}</td>
            <td class="fact muted">{{ formatDateTime(row.endsAt) }}</td>
            <td>
              <span class="state" :data-state="row.hasCredential ? 'CONFIGURED' : 'MISSING'">
                {{ row.hasCredential ? "已录入" : "未录入" }}
              </span>
            </td>
            <td class="muted">{{ row.remark || "—" }}</td>
            <td class="actions">
              <button type="button" class="admin-link" @click="edit(row)">编辑</button>
              <button type="button" class="admin-link danger" @click="pendingDelete = row">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="formMode !== 'hidden'" class="sub-form">
      <h4 class="sub-form-title">{{ formMode === "edit" ? "编辑订阅" : "分配订阅" }}</h4>
      <div class="admin-form">
        <div class="admin-form-row">
          <div class="admin-field">
            <label for="sub-plan">套餐</label>
            <!-- 编辑时套餐锁定：展示分配当时的快照，要换套餐就删了重新分配 -->
            <input
              v-if="formMode === 'edit'"
              id="sub-plan"
              class="admin-input"
              :value="`${editingRow?.name}（${editingRow?.planDurationDays} 天 · ${editingRow?.planPrice} ${editingRow?.planCurrency}）`"
              disabled
            />
            <Select v-else id="sub-plan" v-model="form.planId" :options="planOptions" aria-label="套餐" />
            <p v-if="formMode === 'create' && planOptions.length === 0" class="admin-note">
              没有上架的套餐，先去「套餐管理」新建。
            </p>
          </div>
          <div class="admin-field">
            <label for="sub-agent">Agent 类型</label>
            <!-- agent 类型不可选：由所选套餐决定（编辑时展示分配当时的快照） -->
            <input id="sub-agent" class="admin-input" :value="agentTypeDisplay" disabled />
          </div>
        </div>

        <div class="admin-form-row">
          <div class="admin-field">
            <label for="sub-starts">起期</label>
            <input
              id="sub-starts"
              class="admin-input fact"
              type="datetime-local"
              :value="toDatetimeLocal(form.startsAt)"
              @input="form.startsAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
            />
          </div>
          <div class="admin-field">
            <label for="sub-ends">预计止期</label>
            <input id="sub-ends" class="admin-input fact" :value="predictedEndsAt ?? '选套餐后自动推算'" disabled />
          </div>
        </div>
        <p class="admin-note">
          起期按你的本地时区（<span class="fact">{{ localTimeZone }}</span
          >）填写，保存为绝对时刻；止期 = 起期 + 套餐时长，由服务端推算，不可手改。
        </p>

        <div class="admin-form-row">
          <div class="admin-field">
            <label for="sub-credential">席位凭据</label>
            <input
              id="sub-credential"
              v-model="form.credential"
              class="admin-input"
              type="password"
              :placeholder="formMode === 'edit' ? '留空表示沿用原凭据' : ''"
            />
          </div>
          <div class="admin-field">
            <label for="sub-remark">备注</label>
            <input id="sub-remark" v-model="form.remark" class="admin-input" maxlength="255" />
          </div>
        </div>

        <div class="sub-form-actions">
          <button type="button" class="admin-btn-ghost" @click="formMode = 'hidden'">取消</button>
          <button type="button" class="admin-btn" :disabled="submitting" @click="submit()">
            {{ submitting ? "保存中…" : "保存" }}
          </button>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-if="pendingDelete"
      title="删除确认"
      :message="`确认删除订阅「${pendingDelete.name}」（分配号 ${pendingDelete.assignmentNo}）？`"
      :busy="deleting"
      @confirm="confirmDelete()"
      @cancel="pendingDelete = null"
    />
  </Modal>
</template>

<style scoped>
.sub-form {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.sub-form-title {
  margin-bottom: 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}

.sub-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 分配号 32 位很长，缩字号并截断展示，完整值放 title 里悬停可见 */
.assignment-no {
  max-width: 130px;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
