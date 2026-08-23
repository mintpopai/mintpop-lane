<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type { PlanResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import PlanFormModal from "../components/PlanFormModal.vue";
import { showToast } from "../toast";
import { booleanLabel, formatDateTime, PLACEHOLDER } from "../utils/format";

const plans = ref<PlanResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<PlanResponse | null>(null);
const pendingDelete = ref<PlanResponse | null>(null);
const deleting = ref(false);

const enabledCount = computed(() => plans.value.filter((plan) => plan.enabled).length);

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
  <header class="page-head">
    <h2 class="page-title">套餐</h2>
    <p class="page-facts">
      共 <span class="fact">{{ plans.length }}</span> 个 · 上架
      <span class="fact">{{ enabledCount }}</span> 个。套餐是固定时长与定价的可售卖选项，停用不删除。
    </p>
  </header>

  <div class="admin-toolbar">
    <span class="spacer" />
    <button type="button" class="admin-btn" @click="create()">新建套餐</button>
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="plans.length === 0" class="admin-hint">还没有套餐，点右上角「新建套餐」加一个。</p>
    <table v-else class="admin-table sticky-actions">
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
        <tr v-for="row in plans" :key="row.id">
          <td>{{ row.name }}</td>
          <td>{{ AGENT_TYPE_LABELS[row.agentType as keyof typeof AGENT_TYPE_LABELS] ?? row.agentType }}</td>
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
  </div>

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
