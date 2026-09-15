<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { REBIND_REQUEST_STATUS_LABELS } from "../api/types";
import type { AdminDeviceRebindRequestResponse, DeviceBrief } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import TruncatedText from "../components/TruncatedText.vue";
import ViewTabs from "../components/ViewTabs.vue";
import { useRebindStore } from "../stores/rebind";
import { showToast } from "../toast";
import { formatAssignmentNo, formatDateTime } from "../utils/format";

/** 待办与历史两档。默认待办——这页存在的理由就是「有东西等我处理」 */
type RequestTab = "PENDING" | "ALL";
/** 待决的一次处理：哪条申请、是同意还是拒绝 */
type Decision = { row: AdminDeviceRebindRequestResponse; approve: boolean };

const rows = ref<AdminDeviceRebindRequestResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const currentTab = ref<RequestTab>("PENDING");
const pending = ref<Decision | null>(null);
const deciding = ref(false);

const rebind = useRebindStore();

const pendingRows = computed(() => rows.value.filter((row) => row.status === "PENDING"));

const tabOptions = computed(() => [
  { value: "PENDING" as const, label: "待处理", count: pendingRows.value.length },
  { value: "ALL" as const, label: "全部", count: rows.value.length },
]);

const visibleRows = computed(() =>
  currentTab.value === "PENDING" ? pendingRows.value : rows.value,
);

/** 一条申请都没有，和「待办清空了」是两件事，说法与心情都不同 */
const emptyText = computed(() =>
  rows.value.length === 0
    ? "还没有换机申请。用户在新设备上选中已绑定别处的订阅时，可以从客户端提交申请。"
    : "待处理的申请都处理完了。",
);

/**
 * 设备三要素压成一行：主机名是主角，系统与机型是辨认用的补充。
 * fromDevice / toDevice 均可为 null——不是「未绑定」，而是对应的设备行事后被删除了
 * （服务端语义：申请指向的订阅此前必然已绑定过设备，故 fromDevice 为 null 同样是
 * 设备记录已不存在，不是「此前未绑定」）。如实说明，不画成空格或误导性的「未绑定」。
 */
function deviceText(device: DeviceBrief | null): string {
  return device === null ? "设备记录已不存在" : `${device.name}（${device.os} · ${device.model}）`;
}

const confirmTitle = computed(() => (pending.value?.approve ? "同意换机" : "拒绝换机"));

/* 确认文案里必须带分配号：同一用户可能买了多份同套餐的授权，套餐名与止期都一样，
   只有分配号能把「这次批的是哪一份」说清楚 */
const confirmMessage = computed(() => {
  const decision = pending.value;
  if (decision === null) {
    return "";
  }
  const label = `${decision.row.userEmail} 的订阅「${decision.row.subscriptionName}」（分配号 ${formatAssignmentNo(decision.row.assignmentNo)}）`;
  return decision.approve
    ? `确认把 ${label} 改绑到「${deviceText(decision.row.toDevice)}」？原设备将立即无法使用它。`
    : `确认拒绝 ${label} 的换机申请？该订阅继续绑定在原设备上。`;
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await adminApi().listDeviceRebindRequests();
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

async function confirmDecision(): Promise<void> {
  const decision = pending.value;
  if (decision === null) {
    return;
  }
  deciding.value = true;
  try {
    const api = adminApi();
    if (decision.approve) {
      await api.approveDeviceRebindRequest(decision.row.id);
    } else {
      await api.rejectDeviceRebindRequest(decision.row.id);
    }
    pending.value = null;
    showToast("success", decision.approve ? "已同意，用户刷新席位后即可使用" : "已拒绝该申请");
    await load();
    // 导航轨的角标与这页是两份数据，处理完要一并更新，否则角标会一直挂着已清掉的待办
    await rebind.refresh();
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : (error as Error).message);
  } finally {
    deciding.value = false;
  }
}

onMounted(load);
</script>

<template>
  <PageHead title="换机申请">
    <template #facts>
      待处理 <span class="fact">{{ pendingRows.length }}</span> 条 · 累计
      <span class="fact">{{ rows.length }}</span>
      条。一份订阅只能在一台设备上使用，用户换机需要在这里放行。
    </template>
  </PageHead>

  <ViewTabs v-model="currentTab" :options="tabOptions" label="按处理状态分" />

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="visibleRows.length === 0"
    :empty-text="emptyText"
  >
    <table class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>用户</th>
          <th>订阅</th>
          <th>原设备</th>
          <th>新设备</th>
          <th>理由</th>
          <th>提交时间</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in visibleRows" :key="row.id">
          <td>{{ row.userEmail }}</td>
          <td>
            <!-- 订阅已被删除时 subscriptionName / assignmentNo 均为空串，不是占位文案：
                 整段隐藏，不画出一个 "" 加「分配号 —」这种半截形态 -->
            <template v-if="row.subscriptionName">
              {{ row.subscriptionName }}
              <span class="fact">{{ formatAssignmentNo(row.assignmentNo) }}</span>
            </template>
          </td>
          <td class="muted">{{ deviceText(row.fromDevice) }}</td>
          <td>{{ deviceText(row.toDevice) }}</td>
          <!-- 理由是用户自由输入，直接铺开会把整张表越撑越长 -->
          <td class="muted"><TruncatedText :text="row.reason" /></td>
          <td class="fact muted">{{ formatDateTime(row.createdAt) }}</td>
          <td>
            <span class="state" :data-state="row.status === 'PENDING' ? 'MISSING' : 'ENABLED'">
              {{ REBIND_REQUEST_STATUS_LABELS[row.status] }}
            </span>
          </td>
          <td class="actions">
            <!-- 已处理的申请不给按钮：点了必被服务端拒，不如不画。
                 订阅/设备记录已被删除的申请仍给按钮：拒绝该请求不该靠前端猜测所有失败成因，
                 服务端已有明确错误码（如 410008），走既有 toast 路径原样转述即可 -->
            <template v-if="row.status === 'PENDING'">
              <button type="button" class="admin-link" @click="pending = { row, approve: true }">
                同意
              </button>
              <button
                type="button"
                class="admin-link danger"
                @click="pending = { row, approve: false }"
              >
                拒绝
              </button>
            </template>
          </td>
        </tr>
      </tbody>
    </table>
  </DataCard>

  <ConfirmDialog
    v-if="pending"
    :title="confirmTitle"
    :message="confirmMessage"
    :confirm-text="pending.approve ? '同意' : '拒绝'"
    :busy="deciding"
    @confirm="confirmDecision()"
    @cancel="pending = null"
  />
</template>
