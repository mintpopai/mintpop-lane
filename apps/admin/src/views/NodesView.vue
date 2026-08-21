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
import { booleanLabel, formatDateTime, joinOrDash } from "../utils/format";

const 当前角色 = ref<NodeRole>("FRONT");
const 全部节点 = ref<AdminNodeResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const 弹窗打开 = ref(false);
const 正在编辑 = ref<AdminNodeResponse | null>(null);
const 待删除 = ref<AdminNodeResponse | null>(null);
const deleting = ref(false);

// —— 分组 ——
const 分组列表 = ref<NodeGroupResponse[]>([]);
// "ALL"=全部；"NONE"=未分组；数字=某分组 id
const 当前分组 = ref<"ALL" | "NONE" | number>("ALL");
const 导入弹窗打开 = ref(false);
const 重拉分组 = ref<NodeGroupResponse | null>(null);
const 改名分组 = ref<NodeGroupResponse | null>(null);
const 改名输入 = ref("");
const renaming = ref(false);
const 待删除分组 = ref<NodeGroupResponse | null>(null);
const deletingGroup = ref(false);

const 当前列表 = computed(() =>
  全部节点.value.filter((node) => {
    if (node.role !== 当前角色.value) {
      return false;
    }
    if (当前角色.value !== "FRONT" || 当前分组.value === "ALL") {
      return true;
    }
    return 当前分组.value === "NONE" ? node.groupId === null : node.groupId === 当前分组.value;
  }),
);

// 当前选中的分组对象；选中态只可能来自 chips 点击，正常恒能找到，找不到时按钮区整体不渲染
const 选中分组 = computed(() =>
  typeof 当前分组.value === "number"
    ? (分组列表.value.find((g) => g.id === 当前分组.value) ?? null)
    : null,
);

async function 加载(): Promise<void> {
  loading.value = true;
  try {
    const [nodes, groups] = await Promise.all([adminApi().listNodes(), adminApi().listNodeGroups()]);
    全部节点.value = nodes;
    分组列表.value = groups;
    // 当前选中的分组被删掉后回落到「全部」
    if (typeof 当前分组.value === "number" && !groups.some((g) => g.id === 当前分组.value)) {
      当前分组.value = "ALL";
    }
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

function 新建(): void {
  正在编辑.value = null;
  弹窗打开.value = true;
}

function 编辑(node: AdminNodeResponse): void {
  正在编辑.value = node;
  弹窗打开.value = true;
}

async function 确认删除(): Promise<void> {
  if (!待删除.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deleteNode(待删除.value.id);
    showToast("success", "已删除");
    待删除.value = null;
    await 加载();
  } catch (error) {
    // 410003：仍被用户引用。服务端给的中文提示直接用，不另编一套话术
    showToast("error", error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
  } finally {
    deleting.value = false;
  }
}

function 打开重拉(group: NodeGroupResponse): void {
  重拉分组.value = group;
}

function 打开改名(group: NodeGroupResponse): void {
  改名分组.value = group;
  改名输入.value = group.name;
}

async function 确认改名(): Promise<void> {
  if (!改名分组.value) {
    return;
  }
  if (!改名输入.value.trim()) {
    showToast("error", "分组名不能为空");
    return;
  }
  renaming.value = true;
  try {
    await adminApi().renameNodeGroup(改名分组.value.id, {
      name: 改名输入.value.trim(),
      remark: 改名分组.value.remark ?? "",
    });
    showToast("success", "已改名");
    改名分组.value = null;
    await 加载();
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : `改名失败：${(error as Error).message}`);
  } finally {
    renaming.value = false;
  }
}

async function 确认删除分组(): Promise<void> {
  if (!待删除分组.value) {
    return;
  }
  deletingGroup.value = true;
  try {
    await adminApi().deleteNodeGroup(待删除分组.value.id);
    showToast("success", "已删除分组及其节点");
    待删除分组.value = null;
    await 加载();
  } catch (error) {
    // 410013：组内有节点被用户绑定。服务端中文提示直接用
    showToast("error", error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
  } finally {
    deletingGroup.value = false;
  }
}

onMounted(加载);
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">节点池</h2>
    <p class="page-facts">
      第一跳 <span class="fact">{{ 全部节点.filter((n) => n.role === "FRONT").length }}</span> 个 · 落地
      <span class="fact">{{ 全部节点.filter((n) => n.role === "LAND").length }}</span> 个。
      落地节点一人一座，占用者在表里直接可见。
    </p>
  </header>

  <div class="admin-toolbar">
    <button
      v-for="(label, value) in NODE_ROLE_LABELS"
      :key="value"
      type="button"
      class="admin-chip"
      :class="{ active: 当前角色 === value }"
      @click="当前角色 = value"
    >
      {{ label }}
    </button>
    <span class="spacer" />
    <button v-if="当前角色 === 'FRONT'" type="button" class="admin-btn-ghost" @click="导入弹窗打开 = true">
      从订阅导入
    </button>
    <button type="button" class="admin-btn" @click="新建()">新建节点</button>
  </div>

  <div v-if="当前角色 === 'FRONT'" class="admin-toolbar">
    <button
      type="button"
      class="admin-chip"
      :class="{ active: 当前分组 === 'ALL' }"
      @click="当前分组 = 'ALL'"
    >
      全部
    </button>
    <button
      type="button"
      class="admin-chip"
      :class="{ active: 当前分组 === 'NONE' }"
      @click="当前分组 = 'NONE'"
    >
      未分组
    </button>
    <button
      v-for="group in 分组列表"
      :key="group.id"
      type="button"
      class="admin-chip"
      :class="{ active: 当前分组 === group.id }"
      @click="当前分组 = group.id"
    >
      {{ group.name }} <span class="fact">{{ group.nodeCount }}</span>
    </button>
    <!-- 选中某个分组时露出它的操作 -->
    <template v-if="选中分组">
      <span class="spacer" />
      <button type="button" class="admin-link" @click="打开重拉(选中分组)">重新拉取</button>
      <button type="button" class="admin-link" @click="打开改名(选中分组)">改名</button>
      <button type="button" class="admin-link danger" @click="待删除分组 = 选中分组">删除分组</button>
    </template>
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <div v-else class="admin-card">
    <p v-if="当前列表.length === 0" class="admin-hint">这一类还没有节点，点右上角「新建节点」加一个。</p>
    <table v-else class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>节点名</th>
          <th>协议</th>
          <th v-if="当前角色 === 'FRONT'">分组</th>
          <th>地址</th>
          <template v-if="当前角色 === 'LAND'">
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
        <tr v-for="row in 当前列表" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="fact">{{ row.sourceType ?? row.protocol }}</td>
          <td v-if="当前角色 === 'FRONT'">
            <span v-if="row.groupName" class="pill">{{ row.groupName }}</span>
            <span v-else class="muted">—</span>
          </td>
          <td class="fact">{{ row.serverAddr }}:{{ row.port }}</td>
          <template v-if="当前角色 === 'LAND'">
            <td class="fact muted">{{ joinOrDash(row.egressIps) }}</td>
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
            <button type="button" class="admin-link" @click="编辑(row)">编辑</button>
            <button type="button" class="admin-link danger" @click="待删除 = row">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <NodeFormModal
    v-if="弹窗打开"
    :role="当前角色"
    :editing="正在编辑"
    @saved="加载()"
    @close="弹窗打开 = false"
  />
  <ConfirmDialog
    v-if="待删除"
    title="删除确认"
    :message="`确认删除节点「${待删除.name}」？`"
    :busy="deleting"
    @confirm="确认删除()"
    @cancel="待删除 = null"
  />
  <SubImportModal
    v-if="导入弹窗打开"
    :group="null"
    @saved="加载()"
    @close="导入弹窗打开 = false"
  />
  <SubImportModal
    v-if="重拉分组"
    :group="重拉分组"
    @saved="加载()"
    @close="重拉分组 = null"
  />
  <AdminModal v-if="改名分组" :title="`分组改名：${改名分组.name}`" @close="改名分组 = null">
    <div class="admin-form">
      <div class="admin-field">
        <label for="group-rename">分组名</label>
        <input id="group-rename" v-model="改名输入" class="admin-input" />
      </div>
    </div>
    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="改名分组 = null">取消</button>
      <button type="button" class="admin-btn" :disabled="renaming" @click="确认改名()">
        {{ renaming ? "保存中…" : "保存" }}
      </button>
    </template>
  </AdminModal>
  <ConfirmDialog
    v-if="待删除分组"
    title="删除分组确认"
    :message="`确认删除分组「${待删除分组.name}」？组内 ${待删除分组.nodeCount} 个节点会一并删除。`"
    :busy="deletingGroup"
    @confirm="确认删除分组()"
    @cancel="待删除分组 = null"
  />
</template>
