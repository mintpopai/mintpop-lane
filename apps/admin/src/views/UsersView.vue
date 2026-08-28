<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS, USER_ROLE_LABELS, USER_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import Select from "../components/AdminSelect.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
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
const pendingDelete = ref<AdminUserResponse | null>(null);
const deleting = ref(false);
/** 上一次真正发出去的关键词。空态说法看的是「已生效的筛选」，不是输入框里正在打的字 */
const appliedKeyword = ref("");

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
/* 「在不在订阅期」是附加条件不是主视角——多数时候就是看全部，只在排查时收窄一次，
   所以走下拉。维度名由控件自己的 prefix 说，选项里不再重复「订阅」二字 */
const subscriptionFilterOptions = [
  { value: null, label: "全部" },
  { value: true, label: "有在期" },
  { value: false, label: "无在期" },
];
// 「每页」由控件的 prefix 说，选项里不再逐条重复
const pageSizeOptions = [10, 20, 50].map((n) => ({ value: n, label: String(n) }));

const filtering = computed(() => appliedKeyword.value !== "" || subscriptionFilter.value !== null);

/** 一个人都没有和「筛出来是空的」是两件事，说法与下一步动作都不同 */
const emptyText = computed(() =>
  filtering.value
    ? "没有匹配的用户。换个关键词，或者放宽筛选条件再看。"
    : "还没有用户。用户不在这里手工创建——有人在桌面端登录一次，就会自动建档出现在这里。",
);

function reportError(error: unknown, prefix: string): void {
  showToast("error", error instanceof BizError ? error.message : `${prefix}：${(error as Error).message}`);
}

async function loadList(): Promise<void> {
  loading.value = true;
  const trimmed = keyword.value.trim();
  try {
    const result = await adminApi().pageUsers({
      keyword: trimmed,
      hasActiveSubscription: subscriptionFilter.value,
      pageNo: page.value,
      pageSize: pageSize.value,
    });
    list.value = result.records;
    total.value = result.total;
    appliedKeyword.value = trimmed;
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

function clearFilters(): void {
  keyword.value = "";
  subscriptionFilter.value = null;
  search();
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
  <PageHead title="用户">
    <template #facts>
      共 <span class="fact">{{ total }}</span> 人。用户随登录自动建档，这里只管处置态与链路资源分配。
    </template>
  </PageHead>

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
      class="filter-select"
      prefix="订阅"
      :filtered="subscriptionFilter !== null"
      :options="subscriptionFilterOptions"
      @update:model-value="search()"
    />
  </div>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="list.length === 0"
    :empty-text="emptyText"
  >
    <template #empty-action>
      <!-- 用户不能手工新建，所以空态里唯一有意义的动作是把筛选放宽 -->
      <button v-if="filtering" type="button" class="admin-btn-ghost" @click="clearFilters()">
        清除筛选
      </button>
    </template>

    <table class="admin-table sticky-actions">
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
            <!-- 订阅管理是独立页面（弹窗套娃体验太差），这里只做跳转 -->
            <RouterLink class="admin-link" :to="{ name: 'USER_SUBSCRIPTIONS', params: { id: row.id } }">
              订阅
            </RouterLink>
            <button type="button" class="admin-link" @click="editing = row">编辑</button>
            <button type="button" class="admin-link danger" @click="pendingDelete = row">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </DataCard>

  <!-- 一页都翻不动时不摆分页器：它在卡片下方，出现与否不影响上面任何内容的位置。
       翻页期间它留在原地不卸载，所以按钮要连 loading 一起禁用，否则连点会叠着发请求 -->
  <div v-if="!loadError && total > 0" class="admin-pager">
    <button
      type="button"
      class="admin-btn-ghost"
      :disabled="loading || page <= 1"
      @click="goToPage(page - 1)"
    >
      上一页
    </button>
    <span class="info">
      第 <span class="fact">{{ page }}</span> / <span class="fact">{{ totalPages }}</span> 页 · 共
      <span class="fact">{{ total }}</span> 人
    </span>
    <button
      type="button"
      class="admin-btn-ghost"
      :disabled="loading || page >= totalPages"
      @click="goToPage(page + 1)"
    >
      下一页
    </button>
    <span class="spacer" />
    <!-- 每页条数不是筛选，不给 filtered 的高亮；prefix 让它和筛选下拉同一种读法 -->
    <Select v-model="pageSize" prefix="每页" :options="pageSizeOptions" @update:model-value="search()" />
  </div>

  <UserFormModal
    v-if="editing"
    :user="editing"
    :nodes="nodes"
    @saved="refresh()"
    @close="editing = null"
  />
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
