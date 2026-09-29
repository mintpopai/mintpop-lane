<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { NODE_ROLE_LABELS, NODE_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, AirportSubscriptionResponse, NodeRole } from "../api/types";
import Select from "../components/AdminSelect.vue";
import AirportSubscriptionEditModal from "../components/AirportSubscriptionEditModal.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import FilterChips from "../components/FilterChips.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
import AirportSubscriptionAuditModal from "../components/AirportSubscriptionAuditModal.vue";
import NodeProbeModal from "../components/NodeProbeModal.vue";
import PageHead from "../components/PageHead.vue";
import SubImportModal from "../components/SubImportModal.vue";
import ViewTabs from "../components/ViewTabs.vue";
import { showToast } from "../toast";
import { booleanLabel, formatDate, formatDateTime } from "../utils/format";

const currentRole = ref<NodeRole>("FRONT");
const allNodes = ref<AdminNodeResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<AdminNodeResponse | null>(null);
const pendingDelete = ref<AdminNodeResponse | null>(null);
const deleting = ref(false);
/** 正在做连通性检测的落地节点；弹窗打开即探测 */
const probingNode = ref<AdminNodeResponse | null>(null);

// —— 机场订阅 ——
const groupList = ref<AirportSubscriptionResponse[]>([]);
// "ALL"=全部；数字=某订阅 id。迁移后第一跳节点必然属于某个订阅，不再有「未归属」一档
const currentGroup = ref<"ALL" | number>("ALL");
const importModalOpen = ref(false);
const refetchingGroup = ref<AirportSubscriptionResponse | null>(null);
const editingSubscription = ref<AirportSubscriptionResponse | null>(null);
const pendingDeleteGroup = ref<AirportSubscriptionResponse | null>(null);
const deletingGroup = ref(false);

// —— 采购尽调：候选机场是否与库里已有节点撞故障域，只读，不落库。
// 弹窗自身的状态与逻辑都在 AirportSubscriptionAuditModal 组件里，这里只管开关 ——
const auditModalOpen = ref(false);

/** 启用状态筛选，仅落地页签使用：ALL=不筛 */
const currentStatus = ref<"ALL" | "ENABLED" | "DISABLED">("ALL");
// 切到第一跳时状态筛选不再可见，复位避免残留筛选影响计数
watch(currentRole, (role) => {
  if (role === "FRONT") currentStatus.value = "ALL";
});

/* 计数的唯一口径：「选它之后表格里会有多少行」。所以 tab 与 chip 的计数都从这批
   「已经过了状态下拉」的节点里数——否则会出现 chip 写着 12、表格却空着，看着像 bug。
   页头的「共 N 个节点」是例外，那是整页规模，不随筛选变。 */
const allFront = computed(() => allNodes.value.filter((node) => node.role === "FRONT"));
const allLand = computed(() => allNodes.value.filter((node) => node.role === "LAND"));

function keepStatus(node: AdminNodeResponse): boolean {
  // 第一跳节点没有状态，不按状态过滤
  return (
    node.role === "FRONT" || currentStatus.value === "ALL" || node.status === currentStatus.value
  );
}

const frontNodes = computed(() => allFront.value.filter(keepStatus));
const landNodes = computed(() => allLand.value.filter(keepStatus));

/* 一级按跳数分。两跳的表格列都不同（落地多出口 IP / 时区 / 容量三列），合不到一起，
   所以这里没有「全部」一档——与套餐、企业那种「列相同、可以合看」的一级不同。
   各跳的节点数挂在对应 tab 上，比堆在页头副题里更贴近它描述的对象 */
const roleOptions = computed(() =>
  (Object.entries(NODE_ROLE_LABELS) as [NodeRole, string][]).map(([value, label]) => ({
    value,
    label,
    count: value === "FRONT" ? frontNodes.value.length : landNodes.value.length,
  })),
);

/* 二级带只给第一跳：机场订阅是它的主视角（节点本就是按订阅链接成批导进来的），值少、每次都要切、
   计数有意义。落地节点没有订阅，它的二级带就空着——那条带里还有状态下拉，仍然有内容，切 tab 不塌。
   订阅的计数不用服务端的 group.nodeCount：那是全量，与上面的口径对不上 */
const groupOptions = computed(() => [
  { value: "ALL" as const, label: "全部", count: frontNodes.value.length },
  ...groupList.value.map((group) => ({
    value: group.id,
    label: group.name,
    count: frontNodes.value.filter((node) => node.airportSubscriptionId === group.id).length,
  })),
]);

/**
 * 订阅的流量用量百分比。null 表示「该机场没提供额度头」，与 0% 是两回事——
 * 不能把「没数据」显示成「用了 0%」，那是在骗人。total 缺失或非正也一并视为没有数据，
 * 避免除出 NaN / Infinity。封顶 100：机场统计口径可能比订阅端晚一拍，用量偶尔会略超总量。
 */
function quotaPercent(group: AirportSubscriptionResponse): number | null {
  if (group.usedBytes === null || group.totalBytes === null || group.totalBytes <= 0) {
    return null;
  }
  return Math.min(100, Math.round((group.usedBytes / group.totalBytes) * 100));
}

/* 状态对两跳都适用，是附加条件不是主视角，故走下拉、不占常驻带、不带计数 */
const statusOptions: { value: "ALL" | "ENABLED" | "DISABLED"; label: string }[] = [
  { value: "ALL", label: "全部" },
  { value: "ENABLED", label: NODE_STATUS_LABELS.ENABLED },
  { value: "DISABLED", label: NODE_STATUS_LABELS.DISABLED },
];

const currentList = computed(() => {
  // frontNodes / landNodes 已经过了状态下拉，这里只再叠一层订阅
  const kind = currentRole.value === "FRONT" ? frontNodes.value : landNodes.value;
  if (currentRole.value !== "FRONT" || currentGroup.value === "ALL") {
    return kind;
  }
  return kind.filter((node) => node.airportSubscriptionId === currentGroup.value);
});

/* 当前选中的订阅对象。订阅只属于第一跳，所以这里连 role 一起判——否则切到落地 tab 后，
   上一次选中的订阅操作（重新拉取 / 编辑 / 删除订阅）会跟着漏进落地视图的工具条。
   选中态只可能来自 chips 点击，正常恒能找到，找不到时按钮区整体不渲染 */
const selectedGroup = computed(() =>
  currentRole.value === "FRONT" && typeof currentGroup.value === "number"
    ? (groupList.value.find((g) => g.id === currentGroup.value) ?? null)
    : null,
);

/* 这一跳一个节点都没有（区别于「筛出来是空的」）——两者说法与下一步动作都不同。
   这里数的是没过筛选的原始数量：12 个第一跳节点里没有禁用的，说法该是「这一批里没有」，
   不是「还没有第一跳节点」 */
const kindEmpty = computed(() =>
  currentRole.value === "FRONT" ? allFront.value.length === 0 : allLand.value.length === 0,
);

const emptyText = computed(() => {
  if (!kindEmpty.value) {
    return "这一批里没有节点。";
  }
  return currentRole.value === "FRONT"
    ? "还没有第一跳节点。先建机场，再从订阅导入。"
    : "还没有落地节点。落地节点要填出口 IP 与容量，用户的出口就是从这里分配的。";
});

function resetFilters(): void {
  currentGroup.value = "ALL";
  currentStatus.value = "ALL";
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [nodes, groups] = await Promise.all([
      adminApi().listNodes(),
      adminApi().listAirportSubscriptions(),
    ]);
    allNodes.value = nodes;
    groupList.value = groups;
    // 当前选中的订阅被删掉后回落到「全部」
    if (
      typeof currentGroup.value === "number" &&
      !groups.some((g) => g.id === currentGroup.value)
    ) {
      currentGroup.value = "ALL";
    }
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

function create(): void {
  editing.value = null;
  modalOpen.value = true;
}

function edit(node: AdminNodeResponse): void {
  editing.value = node;
  modalOpen.value = true;
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deleteNode(pendingDelete.value.id);
    showToast("success", "已删除");
    pendingDelete.value = null;
    await load();
  } catch (error) {
    // 410003：仍被用户引用。服务端给的中文提示直接用，不另编一套话术
    showToast(
      "error",
      error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`,
    );
  } finally {
    deleting.value = false;
  }
}

function openRefetch(group: AirportSubscriptionResponse): void {
  refetchingGroup.value = group;
}

async function confirmDeleteGroup(): Promise<void> {
  if (!pendingDeleteGroup.value) {
    return;
  }
  deletingGroup.value = true;
  try {
    await adminApi().deleteAirportSubscription(pendingDeleteGroup.value.id);
    showToast("success", "已删除订阅及其节点");
    pendingDeleteGroup.value = null;
    await load();
  } catch (error) {
    // 410013：订阅仍被用户的第一跳列表引用。服务端中文提示直接用
    showToast(
      "error",
      error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`,
    );
  } finally {
    deletingGroup.value = false;
  }
}

onMounted(load);
</script>

<template>
  <PageHead title="节点池">
    <template #facts>
      共 <span class="fact">{{ allNodes.length }}</span> 个节点 ·
      <span class="fact">{{ groupList.length }}</span>
      个订阅。落地节点按容量分配，已绑人数在表里直接可见。
    </template>
    <template #actions>
      <button
        v-if="currentRole === 'FRONT'"
        type="button"
        class="admin-btn-ghost"
        @click="importModalOpen = true"
      >
        从订阅导入
      </button>
      <!-- 采购前的尽调工具：只读探测候选机场，不依赖当前选中的订阅，故放页头而不是工具条 -->
      <button
        v-if="currentRole === 'FRONT'"
        type="button"
        class="admin-btn-ghost"
        @click="auditModalOpen = true"
      >
        尽调
      </button>
      <!-- 第一跳只能来自机场订阅导入，这里不再提供手工新建的口子 -->
      <button v-if="currentRole === 'LAND'" type="button" class="admin-btn" @click="create()">
        新建节点
      </button>
    </template>
  </PageHead>

  <!-- 一级：换的是看哪一跳，用 tab；二级是在这一跳里挑一批看，用 chip。两层不同形，管辖关系才读得出来 -->
  <ViewTabs v-model="currentRole" :options="roleOptions" label="按跳数分" />

  <!-- 订阅额度：机场订阅有流量额度，跑满会让该订阅下几十个节点同时全部失效，所以常驻展示、
       不随「选中哪个订阅」筛选变化——正因为不显眼才最该常驻提醒 -->
  <div v-if="currentRole === 'FRONT' && groupList.length > 0" class="group-quota-panel">
    <div v-for="g in groupList" :key="g.id" class="group-quota-row">
      <span class="group-quota-name">{{ g.name }}</span>
      <span class="muted group-quota-meta">
        {{ g.airportName }} · {{ g.account }} · {{ g.bandwidthMbps }} Mbps · 主用
        <span class="fact">{{ g.primaryUsed }} / {{ g.primaryCapacity }}</span>
      </span>
      <template v-if="quotaPercent(g) !== null">
        <div class="group-quota-bar">
          <div class="group-quota-bar-fill" :style="{ width: `${quotaPercent(g)}%` }" />
        </div>
        <span class="fact group-quota-pct">{{ quotaPercent(g) }}%</span>
      </template>
      <!-- 额度头是否存在，与「用了多少」是两回事：拿不到时绝不能显示成 0%，那是在骗人 -->
      <span v-else class="muted group-quota-pct">机场未提供额度信息</span>
      <span class="fact muted">到期：{{ formatDate(g.expiresAt) }}</span>
      <span class="fact muted">最近拉取：{{ formatDateTime(g.fetchedAt) }}</span>
      <span v-if="g.fetchFailedSince" class="state" data-state="DISABLED">
        拉取失败，自 {{ formatDateTime(g.fetchFailedSince) }} 起：{{ g.lastFetchError }}
      </span>
    </div>
  </div>

  <div class="admin-toolbar">
    <FilterChips
      v-if="currentRole === 'FRONT'"
      v-model="currentGroup"
      :options="groupOptions"
      label="按订阅筛选"
    />
    <Select
      v-if="currentRole === 'LAND'"
      id="node-status-filter"
      v-model="currentStatus"
      class="filter-select"
      prefix="状态"
      :filtered="currentStatus !== 'ALL'"
      :options="statusOptions"
    />

    <!-- 选中某个订阅时露出它的操作。这些是「当前所选批次」的操作，不是页面级操作，
         所以留在筛选带右侧，不上提到页头 -->
    <template v-if="selectedGroup">
      <span class="spacer" />
      <button type="button" class="admin-link" @click="openRefetch(selectedGroup)">重新拉取</button>
      <button type="button" class="admin-link" @click="editingSubscription = selectedGroup">
        编辑
      </button>
      <button type="button" class="admin-link danger" @click="pendingDeleteGroup = selectedGroup">
        删除订阅
      </button>
    </template>
  </div>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="currentList.length === 0"
    :empty-text="emptyText"
  >
    <template #empty-action>
      <!-- 空态说明里许诺了哪几条路，就把哪几条路摆出来。第一跳只有「从订阅导入」——
           第一跳只能来自机场订阅，手工新建这条路已不存在；落地只有「新建节点」 -->
      <template v-if="kindEmpty">
        <button
          v-if="currentRole === 'FRONT'"
          type="button"
          class="admin-btn-ghost"
          @click="importModalOpen = true"
        >
          从订阅导入
        </button>
        <button v-if="currentRole === 'LAND'" type="button" class="admin-btn" @click="create()">
          新建节点
        </button>
      </template>
      <button v-else type="button" class="admin-btn-ghost" @click="resetFilters()">查看全部</button>
    </template>

    <table class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>节点名</th>
          <th>协议</th>
          <th v-if="currentRole === 'FRONT'">订阅</th>
          <th>地址</th>
          <th>故障域</th>
          <template v-if="currentRole === 'LAND'">
            <th>出口 IP</th>
            <th>出口时区</th>
            <th>已绑 / 容量</th>
          </template>
          <th>密码</th>
          <th v-if="currentRole === 'LAND'" class="status-col">状态</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in currentList" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="fact">{{ row.sourceType ?? row.protocol }}</td>
          <td v-if="currentRole === 'FRONT'">
            <span v-if="row.airportSubscriptionName" class="pill">{{
              row.airportSubscriptionName
            }}</span>
            <span v-else class="muted">—</span>
          </td>
          <td class="fact">{{ row.serverAddr }}:{{ row.port }}</td>
          <!-- 故障域＝该节点域名 CNAME 链的终点，只对前置节点有意义；落地节点没有这个概念 -->
          <td
            class="fact muted"
            :title="row.role === 'FRONT' ? (row.failureDomain ?? '尚未解析或解析失败') : undefined"
          >
            {{ row.role === "FRONT" ? (row.failureDomain ?? "未解析") : "—" }}
          </td>
          <template v-if="currentRole === 'LAND'">
            <td class="fact muted">{{ row.egressIp ?? "—" }}</td>
            <td class="fact muted">{{ row.egressTimezone ?? "—" }}</td>
            <td>
              <span v-if="(row.assignedUserCount ?? 0) > 0" class="pill">
                {{ row.assignedUserCount }} / {{ row.capacity }}
              </span>
              <span v-else class="muted">0 / {{ row.capacity }}</span>
            </td>
          </template>
          <td>
            <span class="state" :data-state="row.secretConfigured ? 'CONFIGURED' : 'MISSING'">
              {{ booleanLabel(row.secretConfigured, "已配置", "未配置") }}
            </span>
          </td>
          <td v-if="currentRole === 'LAND'">
            <span class="state" :data-state="row.status">{{ NODE_STATUS_LABELS[row.status] }}</span>
          </td>
          <td class="fact muted">{{ formatDateTime(row.updatedAt) }}</td>
          <td class="actions">
            <!-- 检测只对落地节点开放：前置节点走加密协议，服务端没内核连不了 -->
            <button
              v-if="currentRole === 'LAND'"
              type="button"
              class="admin-link"
              @click="probingNode = row"
            >
              检测
            </button>
            <button type="button" class="admin-link" @click="edit(row)">编辑</button>
            <!-- 第一跳节点由订阅刷新整体对齐，手删下一轮又会回来，不给入口 -->
            <button
              v-if="currentRole === 'LAND'"
              type="button"
              class="admin-link danger"
              @click="pendingDelete = row"
            >
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </DataCard>

  <NodeFormModal
    v-if="modalOpen"
    :role="currentRole"
    :editing="editing"
    @saved="load()"
    @close="modalOpen = false"
  />
  <NodeProbeModal
    v-if="probingNode"
    :node="probingNode"
    @saved="load()"
    @close="probingNode = null"
  />
  <ConfirmDialog
    v-if="pendingDelete"
    title="删除确认"
    :message="`确认删除节点「${pendingDelete.name}」？`"
    :busy="deleting"
    @confirm="confirmDelete()"
    @cancel="pendingDelete = null"
  />
  <SubImportModal
    v-if="importModalOpen"
    :group="null"
    @saved="load()"
    @close="importModalOpen = false"
  />
  <SubImportModal
    v-if="refetchingGroup"
    :group="refetchingGroup"
    @saved="load()"
    @close="refetchingGroup = null"
  />
  <AirportSubscriptionEditModal
    v-if="editingSubscription"
    :subscription="editingSubscription"
    @saved="load()"
    @close="editingSubscription = null"
  />
  <ConfirmDialog
    v-if="pendingDeleteGroup"
    title="删除订阅确认"
    :message="`确认删除订阅「${pendingDeleteGroup.name}」？该订阅下的 ${pendingDeleteGroup.nodeCount} 个节点会一并删除。`"
    :busy="deletingGroup"
    @confirm="confirmDeleteGroup()"
    @cancel="pendingDeleteGroup = null"
  />
  <AirportSubscriptionAuditModal v-if="auditModalOpen" @close="auditModalOpen = false" />
</template>

<style scoped>
/* —— 订阅额度：常驻一条，不随「选中哪个订阅」的筛选变化。
   跑满额度会让整个订阅下的节点同时失效，这条提醒不能只在点开某个订阅时才看得到 —— */
.group-quota-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--color-bg-cloud);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

.group-quota-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.group-quota-name {
  flex: 0 0 auto;
  min-width: 96px;
  font-weight: 600;
  color: var(--color-ink);
}

.group-quota-meta {
  font-size: 12px;
  white-space: nowrap;
}

.group-quota-bar {
  flex: 1 1 160px;
  max-width: 240px;
  height: 6px;
  background: var(--color-border);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.group-quota-bar-fill {
  height: 100%;
  background: var(--color-brand-deep);
  border-radius: var(--radius-pill);
}

.group-quota-pct {
  flex: 0 0 auto;
  min-width: 96px;
}
</style>
