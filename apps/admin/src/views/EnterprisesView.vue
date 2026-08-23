<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type { EnterpriseResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import EnterpriseFormModal from "../components/EnterpriseFormModal.vue";
import { showToast } from "../toast";
import { booleanLabel, formatDateTime, PLACEHOLDER } from "../utils/format";

const enterprises = ref<EnterpriseResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<EnterpriseResponse | null>(null);
const pendingDelete = ref<EnterpriseResponse | null>(null);
const deleting = ref(false);

const enabledCount = computed(() => enterprises.value.filter((e) => e.enabled).length);

/** 未知类型直接展示原始取值，与套餐页一致 */
function agentLabel(agentType: string): string {
  return AGENT_TYPE_LABELS[agentType as keyof typeof AGENT_TYPE_LABELS] ?? agentType;
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
  <header class="page-head">
    <h2 class="page-title">企业</h2>
    <p class="page-facts">
      共 <span class="fact">{{ enterprises.length }}</span> 家 · 启用
      <span class="fact">{{ enabledCount }}</span> 家。分配订阅时可把席位归属到企业，停用不删除。
    </p>
  </header>

  <div class="admin-toolbar">
    <span class="spacer" />
    <button type="button" class="admin-btn" @click="create()">新建企业</button>
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="enterprises.length === 0" class="admin-hint">还没有企业，点右上角「新建企业」加一家。</p>
    <table v-else class="admin-table sticky-actions">
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
        <tr v-for="row in enterprises" :key="row.id">
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
  </div>

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
