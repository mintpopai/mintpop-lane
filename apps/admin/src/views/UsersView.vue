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
import { formatDate, formatDateTime } from "../utils/format";

const list = ref<AdminUserResponse[]>([]);
const nodes = ref<AdminNodeResponse[]>([]);
const keyword = ref("");
const subscriptionFilter = ref<boolean | null>(null);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const loading = ref(true);
const loadError = ref("");
const editing = ref<AdminUserResponse | null>(null);
const subscriptionTarget = ref<AdminUserResponse | null>(null);
const pendingDelete = ref<AdminUserResponse | null>(null);
const deleting = ref(false);

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const subscriptionFilterOptions = [
  { value: null, label: "全部" },
  { value: true, label: "有在期订阅" },
  { value: false, label: "无在期订阅" },
];
const pageSizeOptions = [10, 20, 50].map((n) => ({ value: n, label: `每页 ${n}` }));

function reportError(error: unknown, prefix: string): void {
  showToast("error", error instanceof BizError ? error.message : `${prefix}：${(error as Error).message}`);
}

async function loadList(): Promise<void> {
  loading.value = true;
  try {
    const result = await adminApi().pageUsers({
      keyword: keyword.value.trim(),
      hasActiveSubscription: subscriptionFilter.value,
      pageNo: page.value,
      pageSize: pageSize.value,
    });
    list.value = result.records;
    total.value = result.total;
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

async function loadNodes(): Promise<void> {
  try {
    nodes.value = await adminApi().listNodes();
  } catch (error) {
    reportError(error, "加载节点失败");
  }
}

/** 搜索、筛选、改每页条数都回到第一页 */
function search(): void {
  page.value = 1;
  void loadList();
}

function goToPage(next: number): void {
  if (next < 1 || next > totalPages.value || next === page.value) {
    return;
  }
  page.value = next;
  void loadList();
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deleteUser(pendingDelete.value.id);
    showToast("success", "已删除");
    pendingDelete.value = null;
    await refresh();
  } catch (error) {
    reportError(error, "删除失败");
  } finally {
    deleting.value = false;
  }
}

async function refresh(): Promise<void> {
  await Promise.all([loadList(), loadNodes()]);
}

onMounted(refresh);
</script>

<template>
  <header class="page-head">
    <h2 class="page-title">用户</h2>
    <p class="page-facts">
      共 <span class="fact">{{ total }}</span> 人。用户随登录自动建档，这里只管处置态与链路资源分配。
    </p>
  </header>

  <div class="admin-toolbar">
    <input
      v-model="keyword"
      class="admin-input search"
      placeholder="按邮箱或 Logto user id 搜索"
      @keyup.enter="search()"
    />
    <button type="button" class="admin-btn-ghost" @click="search()">搜索</button>
    <Select
      v-model="subscriptionFilter"
      :options="subscriptionFilterOptions"
      aria-label="按有无在期订阅筛选"
      @update:model-value="search()"
    />
  </div>

  <p v-if="loading" class="admin-hint">加载中……</p>
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>

  <template v-else>
    <div class="admin-card">
      <p v-if="list.length === 0" class="admin-hint">没有匹配的用户。有人在桌面端登录后会出现在这里。</p>
      <table v-else class="admin-table sticky-actions">
        <thead>
          <tr>
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
          <tr v-for="row in list" :key="row.id">
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
            <td class="fact muted">{{ row.egressIp ?? "—" }}</td>
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
              <button type="button" class="admin-link" @click="subscriptionTarget = row">订阅</button>
              <button type="button" class="admin-link" @click="editing = row">编辑</button>
              <button type="button" class="admin-link danger" @click="pendingDelete = row">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="admin-pager">
      <button type="button" class="admin-btn-ghost" :disabled="page <= 1" @click="goToPage(page - 1)">
        上一页
      </button>
      <span class="info">
        第 <span class="fact">{{ page }}</span> / <span class="fact">{{ totalPages }}</span> 页 · 共
        <span class="fact">{{ total }}</span> 人
      </span>
      <button type="button" class="admin-btn-ghost" :disabled="page >= totalPages" @click="goToPage(page + 1)">
        下一页
      </button>
      <span class="spacer" />
      <Select v-model="pageSize" :options="pageSizeOptions" aria-label="每页条数" @update:model-value="search()" />
    </div>
  </template>

  <UserFormModal
    v-if="editing"
    :user="editing"
    :nodes="nodes"
    @saved="refresh()"
    @close="editing = null"
  />
  <SubscriptionModal v-if="subscriptionTarget" :user="subscriptionTarget" @changed="refresh()" @close="subscriptionTarget = null" />
  <ConfirmDialog
    v-if="pendingDelete"
    title="删除确认"
    :message="`确认删除用户「${pendingDelete.email}」？其订阅与落地出口会随之释放。`"
    :busy="deleting"
    @confirm="confirmDelete()"
    @cancel="pendingDelete = null"
  />
</template>

<style scoped>
.search {
  width: 280px;
}
</style>
