<script setup lang="ts">
// 落地节点：自建的第二段线路，决定用户最终的出口 IP；按容量分配给用户
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { NODE_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse } from "../api/types";
import Select from "../components/AdminSelect.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
import NodeProbeModal from "../components/NodeProbeModal.vue";
import PageHead from "../components/PageHead.vue";
import { showToast } from "../toast";
import { booleanLabel, formatDateTime } from "../utils/format";

const allNodes = ref<AdminNodeResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<AdminNodeResponse | null>(null);
const pendingDelete = ref<AdminNodeResponse | null>(null);
const deleting = ref(false);
/** 正在做连通性检测的节点；弹窗打开即探测 */
const probingNode = ref<AdminNodeResponse | null>(null);

/** 启用状态筛选：ALL=不筛。是附加条件不是主视角，故走下拉、不带计数 */
const currentStatus = ref<"ALL" | "ENABLED" | "DISABLED">("ALL");
const statusOptions: { value: "ALL" | "ENABLED" | "DISABLED"; label: string }[] = [
  { value: "ALL", label: "全部" },
  { value: "ENABLED", label: NODE_STATUS_LABELS.ENABLED },
  { value: "DISABLED", label: NODE_STATUS_LABELS.DISABLED },
];

// 接口给的是全量节点，本页只收落地
const landNodes = computed(() => allNodes.value.filter((node) => node.role === "LAND"));
const currentList = computed(() =>
  currentStatus.value === "ALL"
    ? landNodes.value
    : landNodes.value.filter((node) => node.status === currentStatus.value),
);

/* 「一个都没有」与「筛出来是空的」说法和下一步动作都不同，按没过筛选的原始数量判 */
const kindEmpty = computed(() => landNodes.value.length === 0);
const emptyText = computed(() =>
  kindEmpty.value
    ? "还没有落地节点。落地节点要填出口 IP 与容量，用户的出口就是从这里分配的。"
    : "这一批里没有节点。",
);

async function load(): Promise<void> {
  loading.value = true;
  try {
    allNodes.value = await adminApi().listNodes();
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

function edit(node: AdminNodeResponse): void {
  editing.value = node;
  modalOpen.value = true;
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deleteNode(pendingDelete.value.id);
    showToast("success", "已删除");
    pendingDelete.value = null;
    await load();
  } catch (error) {
    // 410003：仍被用户引用。服务端给的中文提示直接用，不另编一套话术
    showToast(
      "error",
      error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`,
    );
  } finally {
    deleting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <PageHead title="落地节点">
    <template #facts>
      共
      <span class="fact">{{ landNodes.length }}</span>
      个落地节点。按容量分配，已绑人数在表里直接可见。
    </template>
    <template #actions>
      <button type="button" class="admin-btn" @click="create()">新建节点</button>
    </template>
  </PageHead>

  <div class="admin-toolbar">
    <Select
      id="node-status-filter"
      v-model="currentStatus"
      class="filter-select"
      prefix="状态"
      :filtered="currentStatus !== 'ALL'"
      :options="statusOptions"
    />
  </div>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="currentList.length === 0"
    :empty-text="emptyText"
  >
    <template #empty-action>
      <button v-if="kindEmpty" type="button" class="admin-btn" @click="create()">新建节点</button>
      <button v-else type="button" class="admin-btn-ghost" @click="currentStatus = 'ALL'">
        查看全部
      </button>
    </template>

    <table class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>节点名</th>
          <th>协议</th>
          <th>地址</th>
          <th>故障域</th>
          <th>出口 IP</th>
          <th>出口时区</th>
          <th>已绑 / 容量</th>
          <th>密码</th>
          <th class="status-col">状态</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in currentList" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="fact">{{ row.sourceType ?? row.protocol }}</td>
          <td class="fact">{{ row.serverAddr }}:{{ row.port }}</td>
          <!-- 故障域只对机场订阅节点有意义，落地节点固定占位 -->
          <td class="fact muted">—</td>
          <td class="fact muted">{{ row.egressIp ?? "—" }}</td>
          <td class="fact muted">{{ row.egressTimezone ?? "—" }}</td>
          <td>
            <span v-if="(row.assignedUserCount ?? 0) > 0" class="pill">
              {{ row.assignedUserCount }} / {{ row.capacity }}
            </span>
            <span v-else class="muted">0 / {{ row.capacity }}</span>
          </td>
          <td>
            <span class="state" :data-state="row.secretConfigured ? 'CONFIGURED' : 'MISSING'">
              {{ booleanLabel(row.secretConfigured, "已配置", "未配置") }}
            </span>
          </td>
          <td>
            <span class="state" :data-state="row.status">{{ NODE_STATUS_LABELS[row.status] }}</span>
          </td>
          <td class="fact muted">{{ formatDateTime(row.updatedAt) }}</td>
          <td class="actions">
            <button type="button" class="admin-link" @click="probingNode = row">检测</button>
            <button type="button" class="admin-link" @click="edit(row)">编辑</button>
            <button type="button" class="admin-link danger" @click="pendingDelete = row">
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </DataCard>

  <NodeFormModal
    v-if="modalOpen"
    role="LAND"
    :editing="editing"
    @saved="load()"
    @close="modalOpen = false"
  />
  <NodeProbeModal
    v-if="probingNode"
    :node="probingNode"
    @saved="load()"
    @close="probingNode = null"
  />
  <ConfirmDialog
    v-if="pendingDelete"
    title="删除确认"
    :message="`确认删除节点「${pendingDelete.name}」？`"
    :busy="deleting"
    @confirm="confirmDelete()"
    @cancel="pendingDelete = null"
  />
</template>
