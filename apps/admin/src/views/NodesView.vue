<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { NODE_ROLE_LABELS, NODE_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, NodeGroupResponse, NodeRole } from "../api/types";
import AdminModal from "../components/AdminModal.vue";
import Select from "../components/AdminSelect.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import FilterChips from "../components/FilterChips.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
import PageHead from "../components/PageHead.vue";
import SubImportModal from "../components/SubImportModal.vue";
import ViewTabs from "../components/ViewTabs.vue";
import { showToast } from "../toast";
import { booleanLabel, formatDateTime } from "../utils/format";

const currentRole = ref<NodeRole>("FRONT");
const allNodes = ref<AdminNodeResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<AdminNodeResponse | null>(null);
const pendingDelete = ref<AdminNodeResponse | null>(null);
const deleting = ref(false);

// —— 分组 ——
const groupList = ref<NodeGroupResponse[]>([]);
// "ALL"=全部；"NONE"=未分组；数字=某分组 id
const currentGroup = ref<"ALL" | "NONE" | number>("ALL");
const importModalOpen = ref(false);
const refetchingGroup = ref<NodeGroupResponse | null>(null);
const renamingGroup = ref<NodeGroupResponse | null>(null);
const renameInput = ref("");
const renaming = ref(false);
const pendingDeleteGroup = ref<NodeGroupResponse | null>(null);
const deletingGroup = ref(false);

/** 启用状态筛选，两跳共用：ALL=不筛 */
const currentStatus = ref<"ALL" | "ENABLED" | "DISABLED">("ALL");

/* 计数的唯一口径：「选它之后表格里会有多少行」。所以 tab 与 chip 的计数都从这批
   「已经过了状态下拉」的节点里数——否则会出现 chip 写着 12、表格却空着，看着像 bug。
   页头的「共 N 个节点」是例外，那是整页规模，不随筛选变。 */
const allFront = computed(() => allNodes.value.filter((node) => node.role === "FRONT"));
const allLand = computed(() => allNodes.value.filter((node) => node.role === "LAND"));

function keepStatus(node: AdminNodeResponse): boolean {
  return currentStatus.value === "ALL" || node.status === currentStatus.value;
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

/* 二级带只给第一跳：分组是它的主视角（节点本就是按订阅链接成批导进来的），值少、每次都要切、
   计数有意义。落地节点没有组，它的二级带就空着——那条带里还有状态下拉，仍然有内容，切 tab 不塌。
   分组的计数不用服务端的 group.nodeCount：那是全量，与上面的口径对不上 */
const groupOptions = computed(() => [
  { value: "ALL" as const, label: "全部", count: frontNodes.value.length },
  {
    value: "NONE" as const,
    label: "未分组",
    count: frontNodes.value.filter((node) => node.groupId === null).length,
  },
  ...groupList.value.map((group) => ({
    value: group.id,
    label: group.name,
    count: frontNodes.value.filter((node) => node.groupId === group.id).length,
  })),
]);

/* 状态对两跳都适用，是附加条件不是主视角，故走下拉、不占常驻带、不带计数 */
const statusOptions: { value: "ALL" | "ENABLED" | "DISABLED"; label: string }[] = [
  { value: "ALL", label: "全部" },
  { value: "ENABLED", label: NODE_STATUS_LABELS.ENABLED },
  { value: "DISABLED", label: NODE_STATUS_LABELS.DISABLED },
];

const currentList = computed(() => {
  // frontNodes / landNodes 已经过了状态下拉，这里只再叠一层分组
  const kind = currentRole.value === "FRONT" ? frontNodes.value : landNodes.value;
  if (currentRole.value !== "FRONT" || currentGroup.value === "ALL") {
    return kind;
  }
  return kind.filter((node) =>
    currentGroup.value === "NONE" ? node.groupId === null : node.groupId === currentGroup.value,
  );
});

/* 当前选中的分组对象。分组只属于第一跳，所以这里连 role 一起判——否则切到落地 tab 后，
   上一次选中的分组操作（重新拉取 / 改名 / 删除分组）会跟着漏进落地视图的工具条。
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
    ? "还没有第一跳节点。手工建一个，或者把机场订阅链接整批导进来。"
    : "还没有落地节点。落地节点要填出口 IP 与容量，用户的出口就是从这里分配的。";
});

function resetFilters(): void {
  currentGroup.value = "ALL";
  currentStatus.value = "ALL";
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [nodes, groups] = await Promise.all([adminApi().listNodes(), adminApi().listNodeGroups()]);
    allNodes.value = nodes;
    groupList.value = groups;
    // 当前选中的分组被删掉后回落到「全部」
    if (typeof currentGroup.value === "number" && !groups.some((g) => g.id === currentGroup.value)) {
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
    showToast("error", error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
  } finally {
    deleting.value = false;
  }
}

function openRefetch(group: NodeGroupResponse): void {
  refetchingGroup.value = group;
}

function openRename(group: NodeGroupResponse): void {
  renamingGroup.value = group;
  renameInput.value = group.name;
}

async function confirmRename(): Promise<void> {
  if (!renamingGroup.value) {
    return;
  }
  if (!renameInput.value.trim()) {
    showToast("error", "分组名不能为空");
    return;
  }
  renaming.value = true;
  try {
    await adminApi().renameNodeGroup(renamingGroup.value.id, {
      name: renameInput.value.trim(),
      remark: renamingGroup.value.remark ?? "",
    });
    showToast("success", "已改名");
    renamingGroup.value = null;
    await load();
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : `改名失败：${(error as Error).message}`);
  } finally {
    renaming.value = false;
  }
}

async function confirmDeleteGroup(): Promise<void> {
  if (!pendingDeleteGroup.value) {
    return;
  }
  deletingGroup.value = true;
  try {
    await adminApi().deleteNodeGroup(pendingDeleteGroup.value.id);
    showToast("success", "已删除分组及其节点");
    pendingDeleteGroup.value = null;
    await load();
  } catch (error) {
    // 410013：组内有节点被用户绑定。服务端中文提示直接用
    showToast("error", error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`);
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
      <span class="fact">{{ groupList.length }}</span> 个分组。落地节点按容量分配，已绑人数在表里直接可见。
    </template>
    <template #actions>
      <button v-if="currentRole === 'FRONT'" type="button" class="admin-btn-ghost" @click="importModalOpen = true">
        从订阅导入
      </button>
      <button type="button" class="admin-btn" @click="create()">新建节点</button>
    </template>
  </PageHead>

  <!-- 一级：换的是看哪一跳，用 tab；二级是在这一跳里挑一批看，用 chip。两层不同形，管辖关系才读得出来 -->
  <ViewTabs v-model="currentRole" :options="roleOptions" label="按跳数分" />

  <div class="admin-toolbar">
    <FilterChips
      v-if="currentRole === 'FRONT'"
      v-model="currentGroup"
      :options="groupOptions"
      label="按分组筛选"
    />
    <Select
      v-model="currentStatus"
      class="filter-select"
      prefix="状态"
      :filtered="currentStatus !== 'ALL'"
      :options="statusOptions"
    />

    <!-- 选中某个分组时露出它的操作。这些是「当前所选批次」的操作，不是页面级操作，
         所以留在筛选带右侧，不上提到页头 -->
    <template v-if="selectedGroup">
      <span class="spacer" />
      <button type="button" class="admin-link" @click="openRefetch(selectedGroup)">重新拉取</button>
      <button type="button" class="admin-link" @click="openRename(selectedGroup)">改名</button>
      <button type="button" class="admin-link danger" @click="pendingDeleteGroup = selectedGroup">删除分组</button>
    </template>
  </div>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="currentList.length === 0"
    :empty-text="emptyText"
  >
    <template #empty-action>
      <!-- 空态说明里许诺了哪几条路，就把哪几条路摆出来，顺序与页头右上那对按钮一致 -->
      <template v-if="kindEmpty">
        <button
          v-if="currentRole === 'FRONT'"
          type="button"
          class="admin-btn-ghost"
          @click="importModalOpen = true"
        >
          从订阅导入
        </button>
        <button type="button" class="admin-btn" @click="create()">新建节点</button>
      </template>
      <button v-else type="button" class="admin-btn-ghost" @click="resetFilters()">查看全部</button>
    </template>

    <table class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>节点名</th>
          <th>协议</th>
          <th v-if="currentRole === 'FRONT'">分组</th>
          <th>地址</th>
          <template v-if="currentRole === 'LAND'">
            <th>出口 IP</th>
            <th>出口时区</th>
            <th>已绑 / 容量</th>
          </template>
          <th>密码</th>
          <th>状态</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in currentList" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="fact">{{ row.sourceType ?? row.protocol }}</td>
          <td v-if="currentRole === 'FRONT'">
            <span v-if="row.groupName" class="pill">{{ row.groupName }}</span>
            <span v-else class="muted">—</span>
          </td>
          <td class="fact">{{ row.serverAddr }}:{{ row.port }}</td>
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
          <td>
            <span class="state" :data-state="row.status">{{ NODE_STATUS_LABELS[row.status] }}</span>
          </td>
          <td class="fact muted">{{ formatDateTime(row.updatedAt) }}</td>
          <td class="actions">
            <button type="button" class="admin-link" @click="edit(row)">编辑</button>
            <button type="button" class="admin-link danger" @click="pendingDelete = row">删除</button>
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
  <AdminModal v-if="renamingGroup" :title="`分组改名：${renamingGroup.name}`" @close="renamingGroup = null">
    <div class="admin-form">
      <div class="admin-field">
        <label for="group-rename">分组名</label>
        <input id="group-rename" v-model="renameInput" class="admin-input" />
      </div>
    </div>
    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="renamingGroup = null">取消</button>
      <button type="button" class="admin-btn" :disabled="renaming" @click="confirmRename()">
        {{ renaming ? "保存中…" : "保存" }}
      </button>
    </template>
  </AdminModal>
  <ConfirmDialog
    v-if="pendingDeleteGroup"
    title="删除分组确认"
    :message="`确认删除分组「${pendingDeleteGroup.name}」？组内 ${pendingDeleteGroup.nodeCount} 个节点会一并删除。`"
    :busy="deletingGroup"
    @confirm="confirmDeleteGroup()"
    @cancel="pendingDeleteGroup = null"
  />
</template>
