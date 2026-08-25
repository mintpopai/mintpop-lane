<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type {
  AdminSubscriptionResponse,
  AdminUserResponse,
  EnterpriseResponse,
  PlanResponse,
} from "../api/types";
import { showToast } from "../toast";
import { fromDatetimeLocal, toDatetimeLocal } from "../utils/datetimeLocal";
import { formatAssignmentNo, formatDateTime } from "../utils/format";
import {
  agentTypeOptions,
  buildSubscriptionCreatePayload,
  buildSubscriptionUpdatePayload,
  computeEndsAt,
  emptySubscriptionForm,
  enterpriseOptionsForAgent,
  planOptionsForAgent,
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
const enterprises = ref<EnterpriseResponse[]>([]);
const submitting = ref(false);
const pendingDelete = ref<AdminSubscriptionResponse | null>(null);
const deleting = ref(false);

/** 管理员当前浏览器时区，标在表单里免得填的人心里没数 */
const localTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

/** 第一步选 agent 类型：只列有上架套餐的类型 */
const agentOptions = computed(() => agentTypeOptions(plans.value));

/** 第二步选套餐：只列所选 agent 类型下的上架套餐 */
const planOptions = computed(() => planOptionsForAgent(plans.value, form.value.agentType));

/**
 * 归属企业同样按所选 agent 类型收窄，另加一个「个人」空档——
 * 归属是可选的，留空即个人订阅。
 */
const enterpriseOptions = computed(() => [
  { value: null, label: "个人（不归属企业）" },
  ...enterpriseOptionsForAgent(enterprises.value, form.value.agentType),
]);

/** 列表里把归属企业 id 还原成名字；企业已被改名或前端没拉到时退回 id */
function enterpriseLabel(enterpriseId: number): string {
  return enterprises.value.find((e) => e.id === enterpriseId)?.name ?? `#${enterpriseId}`;
}

/**
 * 所选归属企业的域名，账号邮箱要照着它校验；未选企业（个人订阅）时为 null。
 * 企业没拉到时也给 null——宁可放行让服务端拦，也别拿不全的数据误伤。
 */
const selectedEnterpriseDomain = computed<string | null>(() => {
  if (form.value.enterpriseId === null) {
    return null;
  }
  return enterprises.value.find((e) => e.id === form.value.enterpriseId)?.domain ?? null;
});

function agentLabel(agentType: string): string {
  return AGENT_TYPE_LABELS[agentType as keyof typeof AGENT_TYPE_LABELS] ?? agentType;
}

/** 换 agent 类型后已选套餐与归属企业随之作废，清掉逼着重挑 */
function onAgentTypeChange(agentType: string | null): void {
  form.value.agentType = agentType;
  form.value.planId = null;
  form.value.enterpriseId = null;
}

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

/**
 * 分配号是管理员要转交给用户的短码。展示成分组形态便于口述，
 * 但复制的是不带连字符的原值——与库里一致，粘进搜索框能直接命中。
 */
async function copyAssignmentNo(row: AdminSubscriptionResponse): Promise<void> {
  try {
    await navigator.clipboard.writeText(row.assignmentNo);
    showToast("success", "分配号已复制");
  } catch {
    showToast("error", "复制失败，请手动选中复制");
  }
}

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

async function loadEnterprises(): Promise<void> {
  try {
    enterprises.value = await adminApi().listEnterprises();
  } catch (error) {
    reportError(error, "企业加载失败");
  }
}

onMounted(() => {
  void loadList();
  void loadPlans();
  void loadEnterprises();
});

function create(): void {
  form.value = emptySubscriptionForm();
  // 只有一种可选类型时替管理员先选上，少点一次
  if (agentOptions.value.length === 1) {
    form.value.agentType = agentOptions.value[0].value;
  }
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
  const errors = validateSubscriptionForm(form.value, mode, selectedEnterpriseDomain.value);
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
  <Modal :title="`订阅管理：${user.email}`" wide @close="emit('close')">
    <!-- 列表与表单二选一整屏切换，不再堆叠在同一屏里 -->
    <template v-if="formMode === 'hidden'">
      <div class="admin-toolbar">
        <span v-if="!loading && !loadError" class="sub-count">共 {{ list.length }} 条</span>
        <span class="spacer" />
        <button type="button" class="admin-btn" @click="create()">分配订阅</button>
      </div>

      <p v-if="loading" class="admin-hint">加载中……</p>
      <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>
      <div v-else-if="list.length === 0" class="admin-card">
        <p class="admin-hint">还没有订阅，点右上角「分配订阅」，先选 Agent 类型再挑套餐。</p>
      </div>
      <!-- 每条订阅通常就一两条，用卡片摊开所有字段，不再塞截断横滚的密表 -->
      <ul v-else class="sub-list">
        <li v-for="row in list" :key="row.id" class="sub-item">
          <div class="sub-item-head">
            <span class="sub-item-name">{{ row.name }}</span>
            <span class="sub-item-spec">{{ row.planDurationDays }} 天 · {{ row.planPrice }} {{ row.planCurrency }}</span>
            <span class="pill muted">{{ agentLabel(row.agentType) }}</span>
            <span class="state" :data-state="row.hasCredential ? 'CONFIGURED' : 'MISSING'">
              {{ row.hasCredential ? "凭据已录入" : "凭据未录入" }}
            </span>
            <span class="sub-item-gap" />
            <div class="sub-item-actions">
              <button type="button" class="admin-link" @click="edit(row)">编辑</button>
              <button type="button" class="admin-link danger" @click="pendingDelete = row">删除</button>
            </div>
          </div>
          <dl class="sub-item-facts">
            <div class="sub-fact">
              <dt>起期</dt>
              <dd class="fact">{{ formatDateTime(row.startsAt) }}</dd>
            </div>
            <div class="sub-fact">
              <dt>止期</dt>
              <dd class="fact">{{ formatDateTime(row.endsAt) }}</dd>
            </div>
            <div class="sub-fact">
              <dt>分配号</dt>
              <dd class="fact">
                <span class="sub-assignment-no">{{ formatAssignmentNo(row.assignmentNo) }}</span>
                <button type="button" class="admin-link" @click="copyAssignmentNo(row)">复制</button>
              </dd>
            </div>
            <div class="sub-fact">
              <dt>归属</dt>
              <dd>{{ row.enterpriseId === null ? "个人" : enterpriseLabel(row.enterpriseId) }}</dd>
            </div>
            <div class="sub-fact">
              <dt>账号邮箱</dt>
              <dd :class="{ fact: row.accountEmail !== null }">{{ row.accountEmail ?? "未录入" }}</dd>
            </div>
            <div v-if="row.remark" class="sub-fact sub-fact-remark">
              <dt>备注</dt>
              <dd>{{ row.remark }}</dd>
            </div>
          </dl>
        </li>
      </ul>
    </template>

    <div v-else class="sub-form">
      <h4 class="sub-form-title">{{ formMode === "edit" ? "编辑订阅" : "分配订阅" }}</h4>
      <div class="admin-form">
        <div class="admin-form-row">
          <div class="admin-field">
            <label for="sub-agent">Agent 类型</label>
            <!-- 编辑时锁定：展示分配当时的快照 -->
            <input
              v-if="formMode === 'edit'"
              id="sub-agent"
              class="admin-input"
              :value="agentLabel(editingRow?.agentType ?? '')"
              disabled
            />
            <Select
              v-else
              id="sub-agent"
              :model-value="form.agentType"
              :options="agentOptions"
              aria-label="Agent 类型"
              @update:model-value="onAgentTypeChange($event as string | null)"
            />
            <p v-if="formMode === 'create' && agentOptions.length === 0" class="admin-note">
              没有上架的套餐，先去「套餐管理」新建。
            </p>
          </div>
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
            <!-- 还没选类型时先占位，选了类型才放开挑套餐 -->
            <input
              v-else-if="form.agentType === null"
              id="sub-plan"
              class="admin-input"
              value="先选 Agent 类型"
              disabled
            />
            <Select v-else id="sub-plan" v-model="form.planId" :options="planOptions" aria-label="套餐" />
          </div>
        </div>

        <div class="admin-form-row">
          <div class="admin-field">
            <label for="sub-enterprise">归属企业</label>
            <!-- 归属随 agent 类型收窄：没定类型就还不知道哪些企业可选 -->
            <input
              v-if="form.agentType === null"
              id="sub-enterprise"
              class="admin-input"
              value="先选 Agent 类型"
              disabled
            />
            <Select
              v-else
              id="sub-enterprise"
              v-model="form.enterpriseId"
              :options="enterpriseOptions"
              aria-label="归属企业"
            />
            <p class="admin-note">只列启用中、且支持该 Agent 类型的企业；留空即个人订阅。</p>
          </div>
          <div class="admin-field">
            <label for="sub-account-email">账号邮箱</label>
            <input
              id="sub-account-email"
              v-model="form.accountEmail"
              class="admin-input"
              type="email"
              maxlength="128"
              :placeholder="
                selectedEnterpriseDomain ? `zhangsan@${selectedEnterpriseDomain}` : 'zhangsan@example.com'
              "
            />
            <p class="admin-note">
              本次分配给用户的是哪个账号，选填。<template v-if="selectedEnterpriseDomain"
                >归属企业时须为 <span class="fact">@{{ selectedEnterpriseDomain }}</span> 的邮箱。</template
              >
            </p>
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
      :message="`确认删除订阅「${pendingDelete.name}」（分配号 ${formatAssignmentNo(pendingDelete.assignmentNo)}）？`"
      :busy="deleting"
      @confirm="confirmDelete()"
      @cancel="pendingDelete = null"
    />
  </Modal>
</template>

<style scoped>
/* 表单是独立视图（与列表整屏切换），不需要再与上方内容做分隔 */
.sub-form {
  margin-top: 4px;
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

.sub-count {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

/* —— 订阅卡片列表 —— */
.sub-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sub-item {
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
}

.sub-item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.sub-item-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}

.sub-item-spec {
  font-size: 13px;
  color: var(--color-ink-secondary);
}

/* 把操作推到头行最右；窄屏头行折行时它自然落到下一行 */
.sub-item-gap {
  flex: 1;
}

.sub-item-actions {
  display: flex;
  gap: 16px;
}

.sub-item-facts {
  margin: 12px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px 48px;
}

.sub-fact {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.sub-fact dt {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.sub-fact dd {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-ink);
}

/* 分配号是 10 位短码，等宽字体排出来才对得齐、也不会把 0 和 O 看混（字母表本就没有 O） */
.sub-assignment-no {
  font-size: 13px;
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
  letter-spacing: 0.04em;
}

/* 备注是自由文本，独占一行随便换行，不跟等宽事实挤 */
.sub-fact-remark {
  flex-basis: 100%;
}
</style>
