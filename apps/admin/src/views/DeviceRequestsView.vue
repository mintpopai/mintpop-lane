<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { REBIND_REQUEST_STATUS, REBIND_REQUEST_STATUS_LABELS } from "../api/types";
import type {
  AdminDeviceRebindRequestResponse,
  DeviceBrief,
  RebindRequestStatus,
} from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import TruncatedText from "../components/TruncatedText.vue";
import ViewTabs from "../components/ViewTabs.vue";
import { useRebindStore } from "../stores/rebind";
import { showToast } from "../toast";
import { deviceLabel, formatAssignmentNo, formatDateTime, relativeTime } from "../utils/format";

/** 待办与历史两档。默认待办——这页存在的理由就是「有东西等我处理」 */
type RequestTab = "PENDING" | "ALL";
/** 待决的一次处理：哪条申请、是同意还是拒绝 */
type Decision = { row: AdminDeviceRebindRequestResponse; approve: boolean };

/* —— 服务端业务码。本仓还没有集中放业务码的地方，先就近落在用到它的这一页，
      不在判断里散落裸数字 —— */
/** 410042：换机申请不存在（多半是被删了） */
const CODE_REBIND_REQUEST_NOT_FOUND = 410042;
/** 410043：该换机申请已被处理（别的管理员抢先了一步） */
const CODE_REBIND_REQUEST_NOT_PENDING = 410043;
/** 410008：订阅不存在（申请指向的订阅已被删除） */
const CODE_SUBSCRIPTION_NOT_FOUND = 410008;

/**
 * 「你手上这行已经不作数了」的业务码。服务端回这三个之一，等于明说界面上摆着的这行是错的，
 * 必须重拉列表与角标。其余失败（断网、5xx）**不**重拉：那时列表多半还是准的，
 * 再去拉一次只会把一个失败叠成两个。
 */
const STALE_ROW_CODES: ReadonlySet<number> = new Set([
  CODE_REBIND_REQUEST_NOT_FOUND,
  CODE_REBIND_REQUEST_NOT_PENDING,
  CODE_SUBSCRIPTION_NOT_FOUND,
]);

/**
 * 状态 → 色档。绿只给「在跑/正常」那一档，故只有已同意配绿；
 * 已拒绝与已作废都是「这条到此为止」，归灰（与 SUSPENDED / DISABLED / UNBOUND 同档）；
 * 待处理是「还有一步要做」，走琥珀。
 */
const STATUS_STATE: Record<RebindRequestStatus, string> = {
  [REBIND_REQUEST_STATUS.PENDING]: "MISSING",
  [REBIND_REQUEST_STATUS.APPROVED]: "ENABLED",
  [REBIND_REQUEST_STATUS.REJECTED]: "CLOSED",
  [REBIND_REQUEST_STATUS.SUPERSEDED]: "CLOSED",
};

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
 * fromDevice / toDevice 均可为 null——不是「未绑定」，而是对应的设备行事后被删除了
 * （服务端语义：申请指向的订阅此前必然已绑定过设备，故 fromDevice 为 null 同样是
 * 设备记录已不存在，不是「此前未绑定」）。如实说明，不画成空格或误导性的「未绑定」。
 * 设备本身怎么压成一行交给 deviceLabel——机型为空时的分隔符处理两个页面共用一份。
 */
function deviceText(device: DeviceBrief | null): string {
  return device === null ? "设备记录已不存在" : deviceLabel(device);
}

const confirmTitle = computed(() => (pending.value?.approve ? "同意换机" : "拒绝换机"));

/* 确认文案里必须带分配号：同一用户可能买了多份同套餐的授权，套餐名与止期都一样，
   只有分配号能把「这次批的是哪一份」说清楚 */
const confirmMessage = computed(() => {
  const decision = pending.value;
  if (decision === null) {
    return "";
  }
  const row = decision.row;
  // 订阅被删除后 subscriptionName / assignmentNo 均为空串：与表格单元格同一条纪律，
  // 不能拼出「空书名号 + 分配号 —」这种半截标识，改用如实的整体说法
  const subscriptionLabel = row.subscriptionName
    ? `订阅「${row.subscriptionName}」（分配号 ${formatAssignmentNo(row.assignmentNo)}）`
    : "一份已被删除的订阅";
  const label = `${row.userEmail} 的${subscriptionLabel}`;
  if (!decision.approve) {
    return `确认拒绝 ${label} 的换机申请？该订阅继续绑定在原设备上。`;
  }
  // 目标设备行事后被删除时，「改绑到「设备记录已不存在」」把异常状态包装成了操作对象，读起来别扭；
  // 换成陈述句更如实。按钮仍照常给出——是否真能改绑交给服务端裁决，不由前端猜测特判
  return row.toDevice === null
    ? `确认处理 ${label} 的换机申请？目标设备记录已不存在，继续可能会被服务端拒绝。`
    : `确认把 ${label} 改绑到「${deviceLabel(row.toDevice)}」？原设备将立即无法使用它。`;
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
    // 导航轨的角标与这页是两份数据，处理完要一并更新，否则角标会一直挂着已清掉的待办
    await reload();
  } catch (error) {
    // 无论哪种失败都先收起确认框：它悬在一条刚刚拒绝了这次操作的行上，
    // 除了「再点一次同一个按钮、再得到同一句报错」别无出路。要重来，重新点开就是
    pending.value = null;
    showToast("error", error instanceof BizError ? error.message : (error as Error).message);
    if (error instanceof BizError && STALE_ROW_CODES.has(error.code)) {
      await reload();
    }
  } finally {
    deciding.value = false;
  }
}

/** 列表与角标是两份数据，凡是「本机这份可能已经过期」的时刻都要一起刷 */
async function reload(): Promise<void> {
  await load();
  await rebind.refresh();
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
    <!-- 多个管理员同时开着这页时，本机这份随时可能过期。给个就地刷新的口子，
         否则只能靠「切走再切回来」这种不像操作的操作 -->
    <template #actions>
      <button type="button" class="admin-btn-ghost" :disabled="loading" @click="reload()">
        刷新
      </button>
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
          <!-- 申请号是飞书卡片里引用这条申请的那个把手。平时靠「用户 + 分配号」就能对上号，
               唯独订阅已被删除的申请这两样都是空的，卡片上的申请号便成了唯一能对上的东西。
               故跟着用户做成次要行：不新增列（这表 1400px 下已经要横向滚动了），
               而且是真正渲染出来的文字——管理员能用浏览器查找直接命中，title 提示做不到 -->
          <td>
            {{ row.userEmail }}
            <span class="cell-sub fact">{{ row.requestNo }}</span>
          </td>
          <td>
            <!-- 订阅已被删除时 subscriptionName / assignmentNo 均为空串，不是占位文案：
                 整段隐藏，不画出一个 "" 加「分配号 —」这种半截形态 -->
            <template v-if="row.subscriptionName">
              {{ row.subscriptionName }}
              <span class="fact">{{ formatAssignmentNo(row.assignmentNo) }}</span>
            </template>
          </td>
          <!-- 最近活跃做成次要行而非新增列：这表 1400px 下已经要横向滚动了。
               它对裁决很有分量——原设备昨天还在用，这次申请多半是想两台一起用；
               绝对时刻挂 title 供悬浮查看，正文给相对说法，免得管理员自己拿今天去减 -->
          <td class="muted">
            {{ deviceText(row.fromDevice) }}
            <span
              v-if="row.fromDevice"
              class="cell-sub fact"
              :title="formatDateTime(row.fromDevice.lastSeenAt)"
            >
              最近活跃 {{ relativeTime(row.fromDevice.lastSeenAt) }}
            </span>
          </td>
          <td>
            {{ deviceText(row.toDevice) }}
            <span
              v-if="row.toDevice"
              class="cell-sub fact"
              :title="formatDateTime(row.toDevice.lastSeenAt)"
            >
              最近活跃 {{ relativeTime(row.toDevice.lastSeenAt) }}
            </span>
          </td>
          <!-- 理由是用户自由输入，直接铺开会把整张表越撑越长 -->
          <td class="muted"><TruncatedText :text="row.reason" /></td>
          <td class="fact muted">{{ formatDateTime(row.createdAt) }}</td>
          <td>
            <span class="state" :data-state="STATUS_STATE[row.status]">
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
