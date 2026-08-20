<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { NODE_ROLE_LABELS, NODE_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, NodeRole } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
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

const 当前列表 = computed(() => 全部节点.value.filter((node) => node.role === 当前角色.value));

async function 加载(): Promise<void> {
  loading.value = true;
  try {
    全部节点.value = await adminApi().listNodes();
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
    <button type="button" class="admin-btn" @click="新建()">新建节点</button>
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
          <td class="fact">{{ row.protocol }}</td>
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
</template>
