<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS, USER_ROLE_LABELS, USER_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import Select from "../components/AdminSelect.vue";
import SubscriptionModal from "../components/SubscriptionModal.vue";
import UserFormModal from "../components/UserFormModal.vue";
import { showToast } from "../toast";
import { formatDate, formatDateTime, joinOrDash } from "../utils/format";

const 列表 = ref<AdminUserResponse[]>([]);
const 节点 = ref<AdminNodeResponse[]>([]);
const 关键字 = ref("");
const 订阅筛选 = ref<boolean | null>(null);
const 页码 = ref(1);
const 每页 = ref(20);
const 总数 = ref(0);
const loading = ref(true);
const loadError = ref("");
const 正在编辑 = ref<AdminUserResponse | null>(null);
const 订阅目标 = ref<AdminUserResponse | null>(null);
const 待删除 = ref<AdminUserResponse | null>(null);
const deleting = ref(false);

const 总页数 = computed(() => Math.max(1, Math.ceil(总数.value / 每页.value)));
const 订阅筛选选项 = [
  { value: null, label: "全部" },
  { value: true, label: "有在期订阅" },
  { value: false, label: "无在期订阅" },
];
const 每页选项 = [10, 20, 50].map((n) => ({ value: n, label: `每页 ${n}` }));

function 报错(error: unknown, 前缀: string): void {
  showToast("error", error instanceof BizError ? error.message : `${前缀}：${(error as Error).message}`);
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
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
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

/** 搜索、筛选、改每页条数都回到第一页 */
function 搜索(): void {
  页码.value = 1;
  void 加载列表();
}

function 翻页(next: number): void {
  if (next < 1 || next > 总页数.value || next === 页码.value) {
    return;
  }
  页码.value = next;
  void 加载列表();
}

async function 确认删除(): Promise<void> {
  if (!待删除.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deleteUser(待删除.value.id);
    showToast("success", "已删除");
    待删除.value = null;
    await 刷新();
  } catch (error) {
    报错(error, "删除失败");
  } finally {
    deleting.value = false;
  }
}

async function 刷新(): Promise<void> {
  await Promise.all([加载列表(), 加载节点()]);
}

onMounted(刷新);
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">用户</h2>
    <p class="page-facts">
      共 <span class="fact">{{ 总数 }}</span> 人。用户随登录自动建档，这里只管处置态与链路资源分配。
    </p>
  </header>

  <div class="admin-toolbar">
    <input
      v-model="关键字"
      class="admin-input search"
      placeholder="按姓名、邮箱或 Logto user id 搜索"
      @keyup.enter="搜索()"
    />
    <button type="button" class="admin-btn-ghost" @click="搜索()">搜索</button>
    <Select
      v-model="订阅筛选"
      :options="订阅筛选选项"
      aria-label="按有无在期订阅筛选"
      @update:model-value="搜索()"
    />
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else>
    <div class="admin-card">
      <p v-if="列表.length === 0" class="admin-hint">没有匹配的用户。有人在桌面端登录后会出现在这里。</p>
      <table v-else class="admin-table sticky-actions">
        <thead>
          <tr>
            <th>姓名</th>
            <th>邮箱</th>
            <th>Logto user id</th>
            <th>角色</th>
            <th>状态</th>
            <th>第一跳</th>
            <th>落地节点</th>
            <th>出口 IP</th>
            <th>在期订阅</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in 列表" :key="row.id">
            <td>{{ row.name }}</td>
            <td class="fact">{{ row.email }}</td>
            <td class="fact muted">{{ row.subject }}</td>
            <td>{{ USER_ROLE_LABELS[row.role] }}</td>
            <td>
              <span class="state" :data-state="row.status">{{ USER_STATUS_LABELS[row.status] }}</span>
            </td>
            <td>{{ row.frontNodeName ?? "—" }}</td>
            <td>
              <template v-if="row.landNodeName">{{ row.landNodeName }}</template>
              <span v-else class="muted">未分配</span>
            </td>
            <td class="fact muted">{{ joinOrDash(row.egressIps) }}</td>
            <td>
              <template v-if="row.activeSubscriptions.length">
                <span v-for="s in row.activeSubscriptions" :key="s.id" class="pill">
                  {{ AGENT_TYPE_LABELS[s.agentType as keyof typeof AGENT_TYPE_LABELS] ?? s.agentType }}
                  至 <span class="fact">{{ formatDate(s.endsAt) }}</span>
                </span>
              </template>
              <span v-else class="muted">无</span>
            </td>
            <td class="fact muted">{{ formatDateTime(row.updatedAt) }}</td>
            <td class="actions">
              <button type="button" class="admin-link" @click="订阅目标 = row">订阅</button>
              <button type="button" class="admin-link" @click="正在编辑 = row">编辑</button>
              <button type="button" class="admin-link danger" @click="待删除 = row">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-pager">
      <button type="button" class="admin-btn-ghost" :disabled="页码 <= 1" @click="翻页(页码 - 1)">
        上一页
      </button>
      <span class="info">
        第 <span class="fact">{{ 页码 }}</span> / <span class="fact">{{ 总页数 }}</span> 页 · 共
        <span class="fact">{{ 总数 }}</span> 人
      </span>
      <button type="button" class="admin-btn-ghost" :disabled="页码 >= 总页数" @click="翻页(页码 + 1)">
        下一页
      </button>
      <span class="spacer" />
      <Select v-model="每页" :options="每页选项" aria-label="每页条数" @update:model-value="搜索()" />
    </div>
  </template>

  <UserFormModal
    v-if="正在编辑"
    :user="正在编辑"
    :nodes="节点"
    @saved="刷新()"
    @close="正在编辑 = null"
  />
  <SubscriptionModal v-if="订阅目标" :user="订阅目标" @changed="刷新()" @close="订阅目标 = null" />
  <ConfirmDialog
    v-if="待删除"
    title="删除确认"
    :message="`确认删除用户「${待删除.name}」？其订阅与落地出口会随之释放。`"
    :busy="deleting"
    @confirm="确认删除()"
    @cancel="待删除 = null"
  />
</template>

<style scoped>
.search {
  width: 280px;
}
</style>
