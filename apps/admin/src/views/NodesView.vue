<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { NODE_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, NodeRole } from "../api/types";
import { booleanLabel, formatDateTime, joinOrDash } from "../utils/format";
import NodeFormDrawer from "../components/NodeFormDrawer.vue";

const 当前角色 = ref<NodeRole>("FRONT");
const 全部节点 = ref<AdminNodeResponse[]>([]);
const loading = ref(false);
const 抽屉打开 = ref(false);
const 正在编辑 = ref<AdminNodeResponse | null>(null);

const 当前列表 = computed(() => 全部节点.value.filter((node) => node.role === 当前角色.value));

async function 加载(): Promise<void> {
  loading.value = true;
  try {
    全部节点.value = await adminApi().listNodes();
  } catch (error) {
    ElMessage.error(error instanceof BizError ? error.message : `加载失败：${(error as Error).message}`);
  } finally {
    loading.value = false;
  }
}

function 新建(): void {
  正在编辑.value = null;
  抽屉打开.value = true;
}

function 编辑(node: AdminNodeResponse): void {
  正在编辑.value = node;
  抽屉打开.value = true;
}

async function 删除(node: AdminNodeResponse): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除节点「${node.name}」？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await adminApi().deleteNode(node.id);
    ElMessage.success("已删除");
    await 加载();
  } catch (error) {
    // 410003：仍被用户引用。服务端给的中文提示直接用，不另编一套话术
    ElMessage.error(error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
  }
}

onMounted(加载);
</script>

<template>
  <div>
    <div class="page-toolbar">
      <el-radio-group v-model="当前角色">
        <el-radio-button value="FRONT">第一跳（出国）</el-radio-button>
        <el-radio-button value="LAND">第二跳（落地）</el-radio-button>
      </el-radio-group>
      <span class="page-toolbar__spacer" />
      <el-button type="primary" @click="新建()">新建节点</el-button>
    </div>

    <el-table v-loading="loading" :data="当前列表" border>
      <el-table-column prop="name" label="节点名" min-width="150" />
      <el-table-column prop="protocol" label="协议" width="90" />
      <el-table-column label="地址" min-width="200">
        <template #default="{ row }">{{ row.serverAddr }}:{{ row.port }}</template>
      </el-table-column>
      <el-table-column v-if="当前角色 === 'LAND'" label="出口 IP" min-width="160">
        <template #default="{ row }">{{ joinOrDash(row.egressIps) }}</template>
      </el-table-column>
      <el-table-column v-if="当前角色 === 'LAND'" label="占用者" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.assignedUserName" type="info">{{ row.assignedUserName }}</el-tag>
          <span v-else>未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="密码" width="90">
        <template #default="{ row }">
          <el-tag :type="row.secretConfigured ? 'success' : 'warning'">
            {{ booleanLabel(row.secretConfigured, "已配置", "未配置") }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">{{ NODE_STATUS_LABELS[row.status as keyof typeof NODE_STATUS_LABELS] }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="编辑(row)">编辑</el-button>
          <el-button link type="danger" @click="删除(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <NodeFormDrawer v-model="抽屉打开" :role="当前角色" :editing="正在编辑" @saved="加载()" />
  </div>
</template>
