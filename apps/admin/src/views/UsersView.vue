<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { USER_ROLE_LABELS, USER_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import { booleanLabel, formatDateTime, joinOrDash } from "../utils/format";
import { selectableFrontNodes } from "../utils/userForm";
import UserFormDrawer from "../components/UserFormDrawer.vue";

const 列表 = ref<AdminUserResponse[]>([]);
const 节点 = ref<AdminNodeResponse[]>([]);
const 关键字 = ref("");
const 页码 = ref(1);
const 每页 = ref(20);
const 总数 = ref(0);
const loading = ref(false);
const 抽屉打开 = ref(false);
const 正在编辑 = ref<AdminUserResponse | null>(null);

function 报错(error: unknown, 前缀: string): void {
  ElMessage.error(error instanceof BizError ? error.message : `${前缀}：${(error as Error).message}`);
}

async function 加载列表(): Promise<void> {
  loading.value = true;
  try {
    const page = await adminApi().pageUsers({
      keyword: 关键字.value.trim(),
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

function 新建(): void {
  // 第一跳节点非空是数据库层面的硬约束，没有可选节点就不给空表单
  if (selectableFrontNodes(节点.value).length === 0) {
    ElMessage.warning("还没有可用的第一跳节点，请先去「节点池」建一个并启用");
    return;
  }
  正在编辑.value = null;
  抽屉打开.value = true;
}

function 编辑(user: AdminUserResponse): void {
  正在编辑.value = user;
  抽屉打开.value = true;
}

async function 删除(user: AdminUserResponse): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除用户「${user.name}」？其落地出口会随之释放。`, "删除确认", {
      type: "warning",
    });
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
        placeholder="按姓名或 Logto user id 搜索"
        clearable
        style="width: 280px"
        @keyup.enter="搜索()"
        @clear="搜索()"
      />
      <el-button @click="搜索()">搜索</el-button>
      <span class="page-toolbar__spacer" />
      <el-button type="primary" @click="新建()">新建用户</el-button>
    </div>

    <el-table v-loading="loading" :data="列表" border>
      <el-table-column prop="name" label="姓名" width="110" />
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
      <el-table-column label="席位凭据" width="100">
        <template #default="{ row }">
          <el-tag :type="row.credentialConfigured ? 'success' : 'warning'">
            {{ booleanLabel(row.credentialConfigured, "已配置", "未配置") }}
          </el-tag>
        </template>
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

    <UserFormDrawer v-model="抽屉打开" :editing="正在编辑" :nodes="节点" @saved="刷新()" />
  </div>
</template>
