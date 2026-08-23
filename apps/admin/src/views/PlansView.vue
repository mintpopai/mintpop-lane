<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type { PlanResponse } from "../api/types";
import Select from "../components/AdminSelect.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import PlanFormModal from "../components/PlanFormModal.vue";
import ViewTabs from "../components/ViewTabs.vue";
import { showToast } from "../toast";
import { agentLabel, booleanLabel, formatDateTime, PLACEHOLDER } from "../utils/format";

/** 上架状态筛选：ALL=不筛 */
type PlanFilter = "ALL" | "ENABLED" | "DISABLED";

const plans = ref<PlanResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<PlanResponse | null>(null);
const pendingDelete = ref<PlanResponse | null>(null);
const deleting = ref(false);
/** 一级：看哪个 Agent 的套餐。ALL=全部 */
const currentAgent = ref<string>("ALL");
/** 二级：在这个 Agent 里看哪一批 */
const statusFilter = ref<PlanFilter>("ALL");

/** 上架数是整页规模的一部分，挂在页头——状态收进下拉后，这个数在带上就看不到了 */
const enabledCount = computed(() => plans.value.filter((plan) => plan.enabled).length);

/* 一级按 Agent 分：不同 Agent 的套餐是各卖各的两条产品线，价目表分开看才对得上。
   保留「全部」一档——套餐的表格列与 Agent 无关，合在一起看得下去；也兜住服务端
   新增的、本前端还不认识的 Agent 类型（它们进不了下面的具名 tab，但落在「全部」里） */
/* 计数的唯一口径：「选它之后表格里会有多少行」，所以 tab 的计数从这批「已经过了状态下拉」
   的套餐里数。页头的「共 N 个 · 上架 N 个」是例外，那是整页规模，不随筛选变 */
const statusScoped = computed(() =>
  statusFilter.value === "ALL"
    ? plans.value
    : plans.value.filter((plan) => plan.enabled === (statusFilter.value === "ENABLED")),
);

const agentOptions = computed(() => [
  { value: "ALL", label: "全部", count: statusScoped.value.length },
  ...Object.entries(AGENT_TYPE_LABELS).map(([value, label]) => ({
    value,
    label,
    count: statusScoped.value.filter((plan) => plan.agentType === value).length,
  })),
]);

/* 状态是附加条件不是主视角——多数时候就是看全部，只在排查时收窄一次，所以走下拉不占常驻带。
   下拉不带计数：收起来时没人看得见，展开时也不该让人对着数字做决定 */
const statusOptions: { value: PlanFilter; label: string }[] = [
  { value: "ALL", label: "全部" },
  { value: "ENABLED", label: "上架" },
  { value: "DISABLED", label: "停用" },
];

const visiblePlans = computed(() =>
  currentAgent.value === "ALL"
    ? statusScoped.value
    : statusScoped.value.filter((plan) => plan.agentType === currentAgent.value),
);

/** 一个套餐都没有和「筛出来是空的」是两件事，说法与下一步动作都不同 */
const emptyText = computed(() =>
  plans.value.length === 0
    ? "还没有套餐。套餐是一份固定时长与定价的可售卖选项，建好之后才能在用户页分配订阅。"
    : "这一批里没有套餐。",
);

function resetFilters(): void {
  currentAgent.value = "ALL";
  statusFilter.value = "ALL";
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    plans.value = await adminApi().listPlans();
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

function create(): void {
  editing.value = null;
  modalOpen.value = true;
}

function edit(plan: PlanResponse): void {
  editing.value = plan;
  modalOpen.value = true;
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deletePlan(pendingDelete.value.id);
    showToast("success", "已删除");
    pendingDelete.value = null;
    await load();
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
  } finally {
    deleting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <PageHead title="套餐">
    <template #facts>
      共 <span class="fact">{{ plans.length }}</span> 个 · 上架
      <span class="fact">{{ enabledCount }}</span> 个。套餐是固定时长与定价的可售卖选项，停用不删除。
    </template>
    <template #actions>
      <button type="button" class="admin-btn" @click="create()">新建套餐</button>
    </template>
  </PageHead>

  <!-- 一级：看哪个 Agent 的价目表，用 tab（常驻带）；状态是附加条件，收在下面的下拉里 -->
  <ViewTabs v-model="currentAgent" :options="agentOptions" label="按 Agent 分" />

  <div class="admin-toolbar">
    <Select
      v-model="statusFilter"
      class="filter-select"
      prefix="状态"
      :filtered="statusFilter !== 'ALL'"
      :options="statusOptions"
    />
  </div>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="visiblePlans.length === 0"
    :empty-text="emptyText"
  >
    <template #empty-action>
      <button v-if="plans.length === 0" type="button" class="admin-btn" @click="create()">新建套餐</button>
      <button v-else type="button" class="admin-btn-ghost" @click="resetFilters()">查看全部</button>
    </template>

    <table class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>套餐名</th>
          <th>Agent</th>
          <th>时长</th>
          <th>价格</th>
          <th>状态</th>
          <th>备注</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in visiblePlans" :key="row.id">
          <td>{{ row.name }}</td>
          <td>{{ agentLabel(row.agentType) }}</td>
          <td class="fact">{{ row.durationDays }} 天</td>
          <td class="fact">{{ row.price.toFixed(2) }} {{ row.currency }}</td>
          <td>
            <span class="state" :data-state="row.enabled ? 'ENABLED' : 'DISABLED'">
              {{ booleanLabel(row.enabled, "上架", "停用") }}
            </span>
          </td>
          <td class="muted">{{ row.remark || PLACEHOLDER }}</td>
          <td class="fact muted">{{ formatDateTime(row.updatedAt) }}</td>
          <td class="actions">
            <button type="button" class="admin-link" @click="edit(row)">编辑</button>
            <button type="button" class="admin-link danger" @click="pendingDelete = row">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </DataCard>

  <PlanFormModal v-if="modalOpen" :editing="editing" @saved="load()" @close="modalOpen = false" />
  <ConfirmDialog
    v-if="pendingDelete"
    title="删除确认"
    :message="`确认删除套餐「${pendingDelete.name}」？只是下架请改用「停用」。`"
    :busy="deleting"
    @confirm="confirmDelete()"
    @cancel="pendingDelete = null"
  />
</template>
