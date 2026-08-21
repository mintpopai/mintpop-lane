<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type { AdminSubscriptionResponse, AdminUserResponse } from "../api/types";
import { showToast } from "../toast";
import { fromDatetimeLocal, toDatetimeLocal } from "../utils/datetimeLocal";
import { formatDateTime } from "../utils/format";
import {
  buildSubscriptionPayload,
  emptySubscriptionForm,
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
const submitting = ref(false);
const pendingDelete = ref<AdminSubscriptionResponse | null>(null);
const deleting = ref(false);

/** 管理员当前浏览器时区，标在表单里免得填的人心里没数 */
const localTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
// 编辑的这条如果 agentType 不在已知枚举里（服务端新增了本前端还不认识的类型），
// 下拉必须额外补一项原值，否则选项列表里找不到当前值，会显示成空白
const agentOptions = computed<Array<{ value: string; label: string }>>(() => {
  const known = Object.entries(AGENT_TYPE_LABELS).map(([value, label]) => ({ value, label }));
  if (form.value.agentType && !(form.value.agentType in AGENT_TYPE_LABELS)) {
    return [...known, { value: form.value.agentType, label: `${form.value.agentType}（未知类型）` }];
  }
  return known;
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

onMounted(loadList);

function create(): void {
  form.value = emptySubscriptionForm();
  formMode.value = "create";
}

function edit(subscription: AdminSubscriptionResponse): void {
  form.value = subscriptionToForm(subscription);
  formMode.value = "edit";
}

async function submit(): Promise<void> {
  const errors = validateSubscriptionForm(form.value);
  if (errors.length > 0) {
    showToast("error", errors[0]);
    return;
  }

  submitting.value = true;
  try {
    const payload = buildSubscriptionPayload(form.value);
    if (formMode.value === "edit") {
      await adminApi().updateSubscription(form.value.id as number, payload);
    } else {
      await adminApi().createSubscription(props.user.id, payload);
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
      <button type="button" class="admin-btn" @click="create()">新增订阅</button>
    </div>

    <p v-if="loading" class="admin-hint">加载中……</p>
    <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>
    <div v-else class="admin-card">
      <p v-if="list.length === 0" class="admin-hint">还没有订阅，点右上角「新增订阅」录一条。</p>
      <table v-else class="admin-table dense">
        <thead>
          <tr>
            <th>套餐名</th>
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
            <td>{{ row.name }}</td>
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
      <h4 class="sub-form-title">{{ formMode === "edit" ? "编辑订阅" : "新增订阅" }}</h4>
      <div class="admin-form">
        <div class="admin-form-row">
          <div class="admin-field">
            <label for="sub-agent">Agent 类型</label>
            <Select id="sub-agent" v-model="form.agentType" :options="agentOptions" aria-label="Agent 类型" />
          </div>
          <div class="admin-field">
            <label for="sub-name">套餐名</label>
            <input id="sub-name" v-model="form.name" class="admin-input" maxlength="64" />
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
            <label for="sub-ends">止期</label>
            <input
              id="sub-ends"
              class="admin-input fact"
              type="datetime-local"
              :value="toDatetimeLocal(form.endsAt)"
              @input="form.endsAt = fromDatetimeLocal(($event.target as HTMLInputElement).value)"
            />
          </div>
        </div>
        <p class="admin-note">
          起止期按你的本地时区（<span class="fact">{{ localTimeZone }}</span
          >）填写，保存为绝对时刻，各端按各自时区显示。
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
      :message="`确认删除订阅「${pendingDelete.name}」？`"
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
</style>
