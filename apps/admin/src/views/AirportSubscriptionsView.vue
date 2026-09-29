<script setup lang="ts">
// 机场订阅：一家机场一个页签，页签下是这家机场的订阅。订阅里有哪些节点只在排查时才看，
// 下沉到订阅详情页，这里只摆运营关心的额度、名额、到期与拉取状态
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { AirportResponse, AirportSubscriptionResponse } from "../api/types";
import AirportFormModal from "../components/AirportFormModal.vue";
import AirportSubscriptionAuditModal from "../components/AirportSubscriptionAuditModal.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import SubImportModal from "../components/SubImportModal.vue";
import SubscriptionQuota from "../components/SubscriptionQuota.vue";
import TruncatedText from "../components/TruncatedText.vue";
import ViewTabs from "../components/ViewTabs.vue";
import { showToast } from "../toast";
import { formatDate, formatDateTime } from "../utils/format";

const route = useRoute();
const router = useRouter();

const airports = ref<AirportResponse[]>([]);
const subscriptions = ref<AirportSubscriptionResponse[]>([]);
const loading = ref(true);
const loadError = ref("");

const airportModalOpen = ref(false);
const editingAirport = ref<AirportResponse | null>(null);
const pendingDeleteAirport = ref<AirportResponse | null>(null);
const deletingAirport = ref(false);
const importModalOpen = ref(false);
// 采购尽调：只读探测候选机场，不属于任何一家已有机场，故挂在页头
const auditModalOpen = ref(false);

/* 当前机场以 URL 为准：?airport 对得上现存机场就选它，否则（没带、非法、已被删）回落第一家。
   做成 computed 而不是本地状态，删除机场、刷新页面、从详情页返回都自动落对地方。
   唯一的例外是点页签到导航落地之间：每次导航守卫都要实探一次 /api/me，网络远时要等一个往返，
   这段时间先按点中的机场画（pendingAirportId），导航结束（成功或被拒）后交还给 URL */
const pendingAirportId = ref<number | null>(null);
const currentAirport = computed<AirportResponse | null>(() => {
  const wanted = pendingAirportId.value ?? Number(route.query.airport);
  return airports.value.find((a) => a.id === wanted) ?? airports.value[0] ?? null;
});

// 切页签只换 URL 上的 ?airport，用 replace：在几家机场间来回点不该堆出一串「后退」记录
const currentAirportId = computed<number>({
  get: () => currentAirport.value?.id ?? 0,
  set: (id) => {
    pendingAirportId.value = id;
    // 被拒（如守卫把人送去登录）时 URL 没变，清掉 pending 即回到 URL 所指的机场
    void router
      .replace({ query: { ...route.query, airport: String(id) } })
      .catch(() => undefined)
      .finally(() => {
        pendingAirportId.value = null;
      });
  },
});

const tabOptions = computed(() =>
  airports.value.map((a) => ({
    value: a.id,
    label: a.name,
    // 计数用本地分组的结果，与切过去之后表里的行数同一口径
    count: subscriptions.value.filter((s) => s.airportId === a.id).length,
  })),
);

const currentSubscriptions = computed(() =>
  subscriptions.value.filter((s) => s.airportId === currentAirport.value?.id),
);

const totalPrimaryUsed = computed(() => airports.value.reduce((n, a) => n + a.primaryUsed, 0));
const totalPrimaryCapacity = computed(() =>
  airports.value.reduce((n, a) => n + a.primaryCapacity, 0),
);

const emptyText = computed(() =>
  airports.value.length === 0 ? "还没有机场。先新建机场，再导入它的订阅。" : "这家机场还没有订阅。",
);

function fetchFailedTitle(sub: AirportSubscriptionResponse): string {
  return `自 ${formatDateTime(sub.fetchFailedSince)} 起：${sub.lastFetchError ?? "原因未知"}`;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [airportList, subscriptionList] = await Promise.all([
      adminApi().listAirports(),
      adminApi().listAirportSubscriptions(),
    ]);
    airports.value = airportList;
    subscriptions.value = subscriptionList;
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

function createAirport(): void {
  editingAirport.value = null;
  airportModalOpen.value = true;
}

function editAirport(airport: AirportResponse): void {
  editingAirport.value = airport;
  airportModalOpen.value = true;
}

async function confirmDeleteAirport(): Promise<void> {
  if (!pendingDeleteAirport.value) {
    return;
  }
  deletingAirport.value = true;
  try {
    await adminApi().deleteAirport(pendingDeleteAirport.value.id);
    showToast("success", "已删除机场");
    pendingDeleteAirport.value = null;
    // 被删的机场从列表消失后，currentAirport 自动回落第一家
    await load();
  } catch (error) {
    // 410053：机场下还有订阅。服务端给的中文提示直接用
    showToast(
      "error",
      error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`,
    );
  } finally {
    deletingAirport.value = false;
  }
}

/* 整行可点进详情；订阅名本身是链接（键盘可达），点在链接上时交给链接导航，行不再重复 push */
function openDetail(event: MouseEvent, sub: AirportSubscriptionResponse): void {
  if ((event.target as HTMLElement).closest("a")) {
    return;
  }
  void router.push({ name: "SUBSCRIPTION_DETAIL", params: { id: sub.id } });
}

onMounted(load);
</script>

<template>
  <PageHead title="机场订阅">
    <template #facts>
      共 <span class="fact">{{ airports.length }}</span> 家机场 ·
      <span class="fact">{{ subscriptions.length }}</span> 个订阅 · 主用名额
      <span class="fact">{{ totalPrimaryUsed }} / {{ totalPrimaryCapacity }}</span
      >。订阅额度跑满会让整批节点同时失效。
    </template>
    <template #actions>
      <button type="button" class="admin-btn-ghost" @click="auditModalOpen = true">尽调</button>
      <button type="button" class="admin-btn" @click="createAirport()">新建机场</button>
    </template>
  </PageHead>

  <template v-if="currentAirport">
    <ViewTabs v-model="currentAirportId" :options="tabOptions" label="按机场分" />

    <!-- 当前机场自己的事实与操作：随页签切换，所以不上提到页头 -->
    <div class="admin-toolbar airport-bar">
      <span class="airport-bar-facts">
        <a
          v-if="currentAirport.websiteUrl"
          class="fact"
          :href="currentAirport.websiteUrl"
          target="_blank"
          rel="noopener"
          >官网 ↗</a
        >
        <span v-else class="muted">未填官网</span>
        <span v-if="currentAirport.remark" class="muted">
          · <TruncatedText :text="currentAirport.remark" />
        </span>
        <span class="muted">
          · 主用名额
          <span class="fact">
            {{ currentAirport.primaryUsed }} / {{ currentAirport.primaryCapacity }}
          </span>
        </span>
      </span>
      <span class="spacer" />
      <button type="button" class="admin-link" @click="editAirport(currentAirport)">
        编辑机场
      </button>
      <button
        type="button"
        class="admin-link danger"
        @click="pendingDeleteAirport = currentAirport"
      >
        删除机场
      </button>
      <button type="button" class="admin-btn-ghost" @click="importModalOpen = true">
        导入订阅
      </button>
    </div>
  </template>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="currentSubscriptions.length === 0"
    :empty-text="emptyText"
  >
    <template #empty-action>
      <button v-if="airports.length === 0" type="button" class="admin-btn" @click="createAirport()">
        新建机场
      </button>
      <button v-else type="button" class="admin-btn-ghost" @click="importModalOpen = true">
        导入订阅
      </button>
    </template>

    <table class="admin-table">
      <thead>
        <tr>
          <th>订阅名</th>
          <th>账号</th>
          <th>带宽</th>
          <th>主用名额</th>
          <th>流量用量</th>
          <th>到期</th>
          <th>最近拉取</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in currentSubscriptions"
          :key="row.id"
          class="clickable-row"
          @click="openDetail($event, row)"
        >
          <td>
            <RouterLink
              class="admin-link"
              :to="{ name: 'SUBSCRIPTION_DETAIL', params: { id: row.id } }"
            >
              {{ row.name }}
            </RouterLink>
          </td>
          <td class="fact muted">{{ row.account }}</td>
          <td class="fact">{{ row.bandwidthMbps }} Mbps</td>
          <td class="fact">{{ row.primaryUsed }} / {{ row.primaryCapacity }}</td>
          <td><SubscriptionQuota :subscription="row" /></td>
          <td class="fact muted">{{ formatDate(row.expiresAt) }}</td>
          <td>
            <span
              v-if="row.fetchFailedSince"
              class="state fetch-failed"
              data-state="DISABLED"
              :title="fetchFailedTitle(row)"
              >拉取失败</span
            >
            <span v-else class="fact muted">{{ formatDateTime(row.fetchedAt) }}</span>
          </td>
        </tr>
      </tbody>
    </table>
  </DataCard>

  <AirportFormModal
    v-if="airportModalOpen"
    :editing="editingAirport"
    @saved="load()"
    @close="airportModalOpen = false"
  />
  <ConfirmDialog
    v-if="pendingDeleteAirport"
    title="删除机场确认"
    :message="`确认删除机场「${pendingDeleteAirport.name}」？机场下还有订阅时无法删除。`"
    :busy="deletingAirport"
    @confirm="confirmDeleteAirport()"
    @cancel="pendingDeleteAirport = null"
  />
  <SubImportModal
    v-if="importModalOpen"
    :group="null"
    :default-airport-id="currentAirport?.id"
    @saved="load()"
    @close="importModalOpen = false"
  />
  <AirportSubscriptionAuditModal v-if="auditModalOpen" @close="auditModalOpen = false" />
</template>

<style scoped>
/* 布局（换行、spacer 撑开、下边距）沿用全局 .admin-toolbar，这里只调字号与间距 */
.airport-bar {
  gap: 12px;
  font-size: 13px;
}

.airport-bar-facts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

/* 悬停底色用全局 .admin-table tbody tr:hover，这里只补手型 */
.clickable-row {
  cursor: pointer;
}
</style>
