<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { NODE_ROLE_LABELS, NODE_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, NodeGroupResponse, NodeRole } from "../api/types";
import AdminModal from "../components/AdminModal.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
import SubImportModal from "../components/SubImportModal.vue";
import { showToast } from "../toast";
import { booleanLabel, formatDateTime } from "../utils/format";

const currentRole = ref<NodeRole>("FRONT");
const allNodes = ref<AdminNodeResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<AdminNodeResponse | null>(null);
const pendingDelete = ref<AdminNodeResponse | null>(null);
const deleting = ref(false);

// —— 分组 ——
const groupList = ref<NodeGroupResponse[]>([]);
// "ALL"=全部；"NONE"=未分组；数字=某分组 id
const currentGroup = ref<"ALL" | "NONE" | number>("ALL");
const importModalOpen = ref(false);
const refetchingGroup = ref<NodeGroupResponse | null>(null);
const renamingGroup = ref<NodeGroupResponse | null>(null);
const renameInput = ref("");
const renaming = ref(false);
const pendingDeleteGroup = ref<NodeGroupResponse | null>(null);
const deletingGroup = ref(false);

const currentList = computed(() =>
  allNodes.value.filter((node) => {
    if (node.role !== currentRole.value) {
      return false;
    }
    if (currentRole.value !== "FRONT" || currentGroup.value === "ALL") {
      return true;
    }
    return currentGroup.value === "NONE" ? node.groupId === null : node.groupId === currentGroup.value;
  }),
);

// 当前选中的分组对象；选中态只可能来自 chips 点击，正常恒能找到，找不到时按钮区整体不渲染
const selectedGroup = computed(() =>
  typeof currentGroup.value === "number"
    ? (groupList.value.find((g) => g.id === currentGroup.value) ?? null)
    : null,
);

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [nodes, groups] = await Promise.all([adminApi().listNodes(), adminApi().listNodeGroups()]);
    allNodes.value = nodes;
    groupList.value = groups;
    // 当前选中的分组被删掉后回落到「全部」
    if (typeof currentGroup.value === "number" && !groups.some((g) => g.id === currentGroup.value)) {
      currentGroup.value = "ALL";
    }
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
    showToast("error", error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
  } finally {
    deleting.value = false;
  }
}

function openRefetch(group: NodeGroupResponse): void {
  refetchingGroup.value = group;
}

function openRename(group: NodeGroupResponse): void {
  renamingGroup.value = group;
  renameInput.value = group.name;
}

async function confirmRename(): Promise<void> {
  if (!renamingGroup.value) {
    return;
  }
  if (!renameInput.value.trim()) {
    showToast("error", "分组名不能为空");
    return;
  }
  renaming.value = true;
  try {
    await adminApi().renameNodeGroup(renamingGroup.value.id, {
      name: renameInput.value.trim(),
      remark: renamingGroup.value.remark ?? "",
    });
    showToast("success", "已改名");
    renamingGroup.value = null;
    await load();
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : `改名失败：${(error as Error).message}`);
  } finally {
    renaming.value = false;
  }
}

async function confirmDeleteGroup(): Promise<void> {
  if (!pendingDeleteGroup.value) {
    return;
  }
  deletingGroup.value = true;
  try {
    await adminApi().deleteNodeGroup(pendingDeleteGroup.value.id);
    showToast("success", "已删除分组及其节点");
    pendingDeleteGroup.value = null;
    await load();
  } catch (error) {
    // 410013：组内有节点被用户绑定。服务端中文提示直接用
    showToast("error", error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
  } finally {
    deletingGroup.value = false;
  }
}

onMounted(load);
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">节点池</h2>
    <p class="page-facts">
      第一跳 <span class="fact">{{ allNodes.filter((n) => n.role === "FRONT").length }}</span> 个 · 落地
      <span class="fact">{{ allNodes.filter((n) => n.role === "LAND").length }}</span> 个。
      落地节点一人一座，占用者在表里直接可见。
    </p>
  </header>

  <div class="admin-toolbar">
    <button
      v-for="(label, value) in NODE_ROLE_LABELS"
      :key="value"
      type="button"
      class="admin-chip"
      :class="{ active: currentRole === value }"
      @click="currentRole = value"
    >
      {{ label }}
    </button>
    <span class="spacer" />
    <button v-if="currentRole === 'FRONT'" type="button" class="admin-btn-ghost" @click="importModalOpen = true">
      从订阅导入
    </button>
    <button type="button" class="admin-btn" @click="create()">新建节点</button>
  </div>

  <div v-if="currentRole === 'FRONT'" class="admin-toolbar">
    <button
      type="button"
      class="admin-chip"
      :class="{ active: currentGroup === 'ALL' }"
      @click="currentGroup = 'ALL'"
    >
      全部
    </button>
    <button
      type="button"
      class="admin-chip"
      :class="{ active: currentGroup === 'NONE' }"
      @click="currentGroup = 'NONE'"
    >
      未分组
    </button>
    <button
      v-for="group in groupList"
      :key="group.id"
      type="button"
      class="admin-chip"
      :class="{ active: currentGroup === group.id }"
      @click="currentGroup = group.id"
    >
      {{ group.name }} <span class="fact">{{ group.nodeCount }}</span>
    </button>
    <!-- 选中某个分组时露出它的操作 -->
    <template v-if="selectedGroup">
      <span class="spacer" />
      <button type="button" class="admin-link" @click="openRefetch(selectedGroup)">重新拉取</button>
      <button type="button" class="admin-link" @click="openRename(selectedGroup)">改名</button>
      <button type="button" class="admin-link danger" @click="pendingDeleteGroup = selectedGroup">删除分组</button>
    </template>
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="currentList.length === 0" class="admin-hint">这一类还没有节点，点右上角「新建节点」加一个。</p>
    <table v-else class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>节点名</th>
          <th>协议</th>
          <th v-if="currentRole === 'FRONT'">分组</th>
          <th>地址</th>
          <template v-if="currentRole === 'LAND'">
            <th>出口 IP</th>
            <th>占用者</th>
          </template>
          <th>密码</th>
          <th>状态</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in currentList" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="fact">{{ row.sourceType ?? row.protocol }}</td>
          <td v-if="currentRole === 'FRONT'">
            <span v-if="row.groupName" class="pill">{{ row.groupName }}</span>
            <span v-else class="muted">—</span>
          </td>
          <td class="fact">{{ row.serverAddr }}:{{ row.port }}</td>
          <template v-if="currentRole === 'LAND'">
            <td class="fact muted">{{ row.egressIp ?? "—" }}</td>
            <td>
              <span v-if="row.assignedUserName" class="pill">{{ row.assignedUserName }}</span>
              <span v-else class="muted">未分配</span>
            </td>
          </template>
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
            <button type="button" class="admin-link" @click="edit(row)">编辑</button>
            <button type="button" class="admin-link danger" @click="pendingDelete = row">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <NodeFormModal
    v-if="modalOpen"
    :role="currentRole"
    :editing="editing"
    @saved="load()"
    @close="modalOpen = false"
  />
  <ConfirmDialog
    v-if="pendingDelete"
    title="删除确认"
    :message="`确认删除节点「${pendingDelete.name}」？`"
    :busy="deleting"
    @confirm="confirmDelete()"
    @cancel="pendingDelete = null"
  />
  <SubImportModal
    v-if="importModalOpen"
    :group="null"
    @saved="load()"
    @close="importModalOpen = false"
  />
  <SubImportModal
    v-if="refetchingGroup"
    :group="refetchingGroup"
    @saved="load()"
    @close="refetchingGroup = null"
  />
  <AdminModal v-if="renamingGroup" :title="`分组改名：${renamingGroup.name}`" @close="renamingGroup = null">
    <div class="admin-form">
      <div class="admin-field">
        <label for="group-rename">分组名</label>
        <input id="group-rename" v-model="renameInput" class="admin-input" />
      </div>
    </div>
    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="renamingGroup = null">取消</button>
      <button type="button" class="admin-btn" :disabled="renaming" @click="confirmRename()">
        {{ renaming ? "保存中…" : "保存" }}
      </button>
    </template>
  </AdminModal>
  <ConfirmDialog
    v-if="pendingDeleteGroup"
    title="删除分组确认"
    :message="`确认删除分组「${pendingDeleteGroup.name}」？组内 ${pendingDeleteGroup.nodeCount} 个节点会一并删除。`"
    :busy="deletingGroup"
    @confirm="confirmDeleteGroup()"
    @cancel="pendingDeleteGroup = null"
  />
</template>
