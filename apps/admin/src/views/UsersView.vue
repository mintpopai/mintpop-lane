<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS, USER_ROLE_LABELS, USER_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import { formatDateTime, joinOrDash } from "../utils/format";
import SubscriptionDrawer from "../components/SubscriptionDrawer.vue";
import UserFormDrawer from "../components/UserFormDrawer.vue";

const 列表 = ref<AdminUserResponse[]>([]);
const 节点 = ref<AdminNodeResponse[]>([]);
const 关键字 = ref("");
const 订阅筛选 = ref<boolean | null>(null);
const 页码 = ref(1);
const 每页 = ref(20);
const 总数 = ref(0);
const loading = ref(false);
const 抽屉打开 = ref(false);
const 正在编辑 = ref<AdminUserResponse | null>(null);
const 订阅抽屉打开 = ref(false);
const 订阅目标 = ref<AdminUserResponse | null>(null);

function 报错(error: unknown, 前缀: string): void {
  ElMessage.error(error instanceof BizError ? error.message : `${前缀}：${(error as Error).message}`);
}

async function 加载列表(): Promise<void> {
  loading.value = true;
  try {
    const page = await adminApi().pageUsers({
      keyword: 关键字.value.trim(),
      hasActiveSubscription: 订阅筛选.value,
      pageNo: 页码.value,
      pageSize: 每页.value,
    });
    列表.value = page.records;
    总数.value = page.total;
  } catch (error) {
    报错(error, "加载失败");
  } finally {
    loading.value = false;
  }
}

async function 加载节点(): Promise<void> {
  try {
    节点.value = await adminApi().listNodes();
  } catch (error) {
    报错(error, "加载节点失败");
  }
}

function 搜索(): void {
  页码.value = 1;
  void 加载列表();
}

function 筛选变化(): void {
  页码.value = 1;
  void 加载列表();
}

function 编辑(user: AdminUserResponse): void {
  正在编辑.value = user;
  抽屉打开.value = true;
}

function 打开订阅(user: AdminUserResponse): void {
  订阅目标.value = user;
  订阅抽屉打开.value = true;
}

async function 删除(user: AdminUserResponse): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${user.name}」？其订阅与落地出口会随之释放。`,
      "删除确认",
      { type: "warning" },
    );
  } catch {
    return;
  }
  try {
    await adminApi().deleteUser(user.id);
    ElMessage.success("已删除");
    await 刷新();
  } catch (error) {
    报错(error, "删除失败");
  }
}

async function 刷新(): Promise<void> {
  await Promise.all([加载列表(), 加载节点()]);
}

onMounted(刷新);
</script>

<template>
  <div>
    <div class="page-toolbar">
      <el-input
        v-model="关键字"
        placeholder="按姓名、邮箱或 Logto user id 搜索"
        clearable
        style="width: 280px"
        @keyup.enter="搜索()"
        @clear="搜索()"
      />
      <el-button @click="搜索()">搜索</el-button>
      <el-select v-model="订阅筛选" style="width: 160px" @change="筛选变化()">
        <el-option label="全部" :value="null" />
        <el-option label="有在期订阅" :value="true" />
        <el-option label="无在期订阅" :value="false" />
      </el-select>
      <span class="page-toolbar__spacer" />
    </div>

    <el-table v-loading="loading" :data="列表" border>
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column prop="subject" label="Logto user id" min-width="200" show-overflow-tooltip />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">{{ USER_ROLE_LABELS[row.role as keyof typeof USER_ROLE_LABELS] }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ USER_STATUS_LABELS[row.status as keyof typeof USER_STATUS_LABELS] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="第一跳" min-width="130">
        <template #default="{ row }">{{ row.frontNodeName ?? "—" }}</template>
      </el-table-column>
      <el-table-column label="落地节点" min-width="150">
        <template #default="{ row }">{{ row.landNodeName ?? "未分配" }}</template>
      </el-table-column>
      <el-table-column label="出口 IP" min-width="150">
        <template #default="{ row }">{{ joinOrDash(row.egressIps) }}</template>
      </el-table-column>
      <el-table-column label="在期订阅" min-width="200">
        <template #default="{ row }">
          <template v-if="row.activeSubscriptions.length">
            <el-tag
              v-for="s in row.activeSubscriptions"
              :key="s.id"
              style="margin-right: 4px"
              type="success"
            >
              {{ AGENT_TYPE_LABELS[s.agentType as keyof typeof AGENT_TYPE_LABELS] ?? s.agentType }}
              至 {{ s.endsAt.slice(0, 10) }}
            </el-tag>
          </template>
          <el-tag v-else type="info">无</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="打开订阅(row)">订阅</el-button>
          <el-button link type="primary" @click="编辑(row)">编辑</el-button>
          <el-button link type="danger" @click="删除(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="页码"
      v-model:page-size="每页"
      :total="总数"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="加载列表()"
      @size-change="搜索()"
    />

    <UserFormDrawer
      v-if="正在编辑"
      v-model="抽屉打开"
      :user="正在编辑"
      :nodes="节点"
      @saved="刷新()"
    />
    <SubscriptionDrawer v-if="订阅目标" v-model="订阅抽屉打开" :user="订阅目标" @changed="刷新()" />
  </div>
</template>
