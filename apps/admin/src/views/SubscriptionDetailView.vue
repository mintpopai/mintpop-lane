<script setup lang="ts">
// 订阅详情：一个机场订阅的全部事实、订阅级操作，以及它导入的节点。
// 节点只在排查时才需要看，所以从列表页下沉到这里
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { AdminNodeResponse, AirportSubscriptionResponse } from "../api/types";
import AirportSubscriptionEditModal from "../components/AirportSubscriptionEditModal.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
import SubImportModal from "../components/SubImportModal.vue";
import SubscriptionQuota from "../components/SubscriptionQuota.vue";
import { showToast } from "../toast";
import { booleanLabel, formatDate, formatDateTime } from "../utils/format";

const route = useRoute();
const router = useRouter();

const subscriptions = ref<AirportSubscriptionResponse[]>([]);
const allNodes = ref<AdminNodeResponse[]>([]);
const loading = ref(true);
const loadError = ref("");

const refetching = ref(false);
const editingSubscription = ref(false);
const pendingDelete = ref(false);
const deleting = ref(false);
const editingNode = ref<AdminNodeResponse | null>(null);

// 没有「按 id 取订阅」的接口：订阅最多几十个，拉全量再按 id 找，不为此加后端接口
const subscription = computed(
  () => subscriptions.value.find((s) => s.id === Number(route.params.id)) ?? null,
);

// 接口给的是全量节点：只留本订阅导入的机场订阅节点
const nodes = computed(() =>
  subscription.value
    ? allNodes.value.filter(
        (n) => n.role === "FRONT" && n.airportSubscriptionId === subscription.value!.id,
      )
    : [],
);

// 返回时回到它所属机场的那个页签；订阅没取到时退回机场订阅页默认页签
const backTo = computed(() =>
  subscription.value
    ? { name: "AIRPORT_SUBSCRIPTIONS", query: { airport: String(subscription.value.airportId) } }
    : { name: "AIRPORT_SUBSCRIPTIONS" },
);

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [subscriptionList, nodeList] = await Promise.all([
      adminApi().listAirportSubscriptions(),
      adminApi().listNodes(),
    ]);
    subscriptions.value = subscriptionList;
    allNodes.value = nodeList;
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

async function confirmDelete(): Promise<void> {
  if (!subscription.value) {
    return;
  }
  const airportId = subscription.value.airportId;
  deleting.value = true;
  try {
    await adminApi().deleteAirportSubscription(subscription.value.id);
    showToast("success", "已删除订阅及其节点");
    pendingDelete.value = false;
    await router.push({ name: "AIRPORT_SUBSCRIPTIONS", query: { airport: String(airportId) } });
  } catch (error) {
    // 410013：订阅仍被用户的线路引用。服务端中文提示直接用
    showToast(
      "error",
      error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`,
    );
  } finally {
    deleting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <nav class="back-line">
    <RouterLink class="admin-link" :to="backTo">
      ← 机场订阅<template v-if="subscription"> / {{ subscription.airportName }}</template>
    </RouterLink>
  </nav>

  <p v-if="loading" class="muted">加载中…</p>
  <!-- 接口失败与「订阅不存在」要分开说：前者是故障，后者是数据事实 -->
  <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>
  <p v-else-if="!subscription" class="admin-hint">订阅不存在或已被删除。</p>

  <template v-else>
    <header class="detail-head">
      <h2 class="page-title">{{ subscription.name }}</h2>
      <div class="detail-actions">
        <button type="button" class="admin-link" @click="refetching = true">重新拉取</button>
        <button type="button" class="admin-link" @click="editingSubscription = true">编辑</button>
        <button type="button" class="admin-link danger" @click="pendingDelete = true">
          删除订阅
        </button>
      </div>
    </header>

    <section class="admin-card detail-info">
      <p class="detail-facts">
        机场 {{ subscription.airportName }} · 账号
        <span class="fact">{{ subscription.account }}</span> · 订阅链接
        <span class="fact">{{ subscription.subUrlMasked }}</span> · 带宽
        <span class="fact">{{ subscription.bandwidthMbps }} Mbps</span>
      </p>
      <p class="detail-facts">
        主用名额
        <span class="fact"
          >{{ subscription.primaryUsed }} / {{ subscription.primaryCapacity }}</span
        >
        · 流量 <SubscriptionQuota :subscription="subscription" /> · 到期
        <span class="fact">{{ formatDate(subscription.expiresAt) }}</span> · 最近拉取
        <span class="fact">{{ formatDateTime(subscription.fetchedAt) }}</span>
      </p>
      <p
        v-if="subscription.fetchFailedSince"
        class="state fetch-failed-banner"
        data-state="DISABLED"
      >
        拉取失败，自 {{ formatDateTime(subscription.fetchFailedSince) }} 起：{{
          subscription.lastFetchError ?? "原因未知"
        }}
      </p>
      <p v-if="subscription.remark" class="muted">备注：{{ subscription.remark }}</p>
    </section>

    <h3 class="block-title nodes-title">节点（{{ nodes.length }}）</h3>
    <p v-if="nodes.length === 0" class="muted">
      这个订阅里还没有节点。可以点「重新拉取」再从订阅导入一次。
    </p>
    <section v-else class="admin-card">
      <table class="admin-table sticky-actions">
        <thead>
          <tr>
            <th>节点名</th>
            <th>协议</th>
            <th>地址</th>
            <th>故障域</th>
            <th>密码</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in nodes" :key="row.id">
            <td>{{ row.name }}</td>
            <td class="fact">{{ row.sourceType ?? row.protocol }}</td>
            <td class="fact">{{ row.serverAddr }}:{{ row.port }}</td>
            <!-- 故障域＝该节点域名 CNAME 链的终点 -->
            <td class="fact muted" :title="row.failureDomain ?? '尚未解析或解析失败'">
              {{ row.failureDomain ?? "未解析" }}
            </td>
            <td>
              <span class="state" :data-state="row.secretConfigured ? 'CONFIGURED' : 'MISSING'">
                {{ booleanLabel(row.secretConfigured, "已配置", "未配置") }}
              </span>
            </td>
            <td class="fact muted">{{ formatDateTime(row.updatedAt) }}</td>
            <!-- 订阅节点由订阅刷新整体对齐，手删下一轮又会回来，不给删除入口 -->
            <td class="actions">
              <button type="button" class="admin-link" @click="editingNode = row">编辑</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <SubImportModal
      v-if="refetching"
      :group="subscription"
      @saved="load()"
      @close="refetching = false"
    />
    <AirportSubscriptionEditModal
      v-if="editingSubscription"
      :subscription="subscription"
      @saved="load()"
      @close="editingSubscription = false"
    />
    <ConfirmDialog
      v-if="pendingDelete"
      title="删除订阅确认"
      :message="`确认删除订阅「${subscription.name}」？该订阅下的 ${nodes.length} 个节点会一并删除。`"
      :busy="deleting"
      @confirm="confirmDelete()"
      @cancel="pendingDelete = false"
    />
    <NodeFormModal
      v-if="editingNode"
      role="FRONT"
      :editing="editingNode"
      @saved="load()"
      @close="editingNode = null"
    />
  </template>
</template>

<style scoped>
.back-line {
  margin-bottom: 12px;
}

.detail-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.detail-actions {
  display: flex;
  gap: 12px;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 24px;
  font-size: 13px;
}

.detail-facts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.nodes-title {
  margin-bottom: 12px;
}
</style>
