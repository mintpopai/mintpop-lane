<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type { EnterpriseResponse } from "../api/types";
import Select from "../components/AdminSelect.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import EnterpriseFormModal from "../components/EnterpriseFormModal.vue";
import PageHead from "../components/PageHead.vue";
import ViewTabs from "../components/ViewTabs.vue";
import { showToast } from "../toast";
import { agentLabel, booleanLabel, formatDateTime, PLACEHOLDER } from "../utils/format";

/** 启用状态筛选：ALL=不筛 */
type EnterpriseFilter = "ALL" | "ENABLED" | "DISABLED";

const enterprises = ref<EnterpriseResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<EnterpriseResponse | null>(null);
const pendingDelete = ref<EnterpriseResponse | null>(null);
const deleting = ref(false);
/** 一级：看支持哪个 Agent 的企业。ALL=全部 */
const currentAgent = ref<string>("ALL");
/** 二级：在这一类里看哪一批 */
const statusFilter = ref<EnterpriseFilter>("ALL");

/** 启用数是整页规模的一部分，挂在页头——状态收进下拉后，这个数在带上就看不到了 */
const enabledCount = computed(() => enterprises.value.filter((e) => e.enabled).length);

/* 一级按 Agent 分，与套餐页同一套。注意企业的 agentTypes 是多值：一家支持两个 Agent 的企业
   会同时出现在两个 tab 下，各 tab 的计数之和因此可能大于总数——这是对的，tab 问的是
   「支持这个 Agent 的有哪些」，不是把企业切成互斥的几堆 */
/* 计数的唯一口径：「选它之后表格里会有多少行」，所以 tab 的计数从这批「已经过了状态下拉」
   的企业里数。页头的「共 N 家 · 启用 N 家」是例外，那是整页规模，不随筛选变 */
const statusScoped = computed(() =>
  statusFilter.value === "ALL"
    ? enterprises.value
    : enterprises.value.filter((e) => e.enabled === (statusFilter.value === "ENABLED")),
);

const agentOptions = computed(() => [
  { value: "ALL", label: "全部", count: statusScoped.value.length },
  ...Object.entries(AGENT_TYPE_LABELS).map(([value, label]) => ({
    value,
    label,
    count: statusScoped.value.filter((e) => e.agentTypes.includes(value)).length,
  })),
]);

/* 状态走下拉不占常驻带，与套餐页同一套：它是排查时才用一次的附加条件，不是主视角 */
const statusOptions: { value: EnterpriseFilter; label: string }[] = [
  { value: "ALL", label: "全部" },
  { value: "ENABLED", label: "启用" },
  { value: "DISABLED", label: "停用" },
];

const visibleEnterprises = computed(() =>
  currentAgent.value === "ALL"
    ? statusScoped.value
    : statusScoped.value.filter((e) => e.agentTypes.includes(currentAgent.value)),
);

const emptyText = computed(() =>
  enterprises.value.length === 0
    ? "还没有企业。建好之后，在用户页分配订阅时就能把席位归属到它，账号邮箱按企业域名校验。"
    : "这一批里没有企业。",
);

function resetFilters(): void {
  currentAgent.value = "ALL";
  statusFilter.value = "ALL";
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    enterprises.value = await adminApi().listEnterprises();
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

function edit(enterprise: EnterpriseResponse): void {
  editing.value = enterprise;
  modalOpen.value = true;
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deleteEnterprise(pendingDelete.value.id);
    showToast("success", "已删除");
    pendingDelete.value = null;
    await load();
  } catch (error) {
    // 410025 仍被订阅引用时删不掉，服务端给的中文提示直接用
    showToast("error", error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
  } finally {
    deleting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <PageHead title="企业">
    <template #facts>
      共 <span class="fact">{{ enterprises.length }}</span> 家 · 启用
      <span class="fact">{{ enabledCount }}</span> 家。分配订阅时可把席位归属到企业，停用不删除。
    </template>
    <template #actions>
      <button type="button" class="admin-btn" @click="create()">新建企业</button>
    </template>
  </PageHead>

  <!-- 一级：看支持哪个 Agent 的企业，用 tab（常驻带）；状态是附加条件，收在下面的下拉里 -->
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
    :empty="visibleEnterprises.length === 0"
    :empty-text="emptyText"
  >
    <template #empty-action>
      <button v-if="enterprises.length === 0" type="button" class="admin-btn" @click="create()">
        新建企业
      </button>
      <button v-else type="button" class="admin-btn-ghost" @click="resetFilters()">查看全部</button>
    </template>

    <table class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>企业名称</th>
          <th>域名</th>
          <th>支持的 Agent</th>
          <th>状态</th>
          <th>备注</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in visibleEnterprises" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="fact">{{ row.domain }}</td>
          <td>
            <span v-for="type in row.agentTypes" :key="type" class="pill">{{ agentLabel(type) }}</span>
          </td>
          <td>
            <span class="state" :data-state="row.enabled ? 'ENABLED' : 'DISABLED'">
              {{ booleanLabel(row.enabled, "启用", "停用") }}
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

  <EnterpriseFormModal
    v-if="modalOpen"
    :editing="editing"
    @saved="load()"
    @close="modalOpen = false"
  />
  <ConfirmDialog
    v-if="pendingDelete"
    title="删除确认"
    :message="`确认删除企业「${pendingDelete.name}」？只是不再分配请改用「停用」。`"
    :busy="deleting"
    @confirm="confirmDelete()"
    @cancel="pendingDelete = null"
  />
</template>
