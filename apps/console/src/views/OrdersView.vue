<script setup lang="ts">
// 我的订单：一张表，待支付的能继续付或取消；已支付的告诉你订阅去哪了
import { onMounted, ref } from "vue";
import { consoleApi } from "../api";
import { ORDER_STATUS, ORDER_STATUS_LABELS, type OrderResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import { showToast } from "../toast";
import { agentLabel, formatAmount, formatDateTime } from "../utils/format";

const loading = ref(true);
const loadError = ref("");
const orders = ref<OrderResponse[]>([]);
const pendingCancel = ref<OrderResponse | null>(null);
const cancelling = ref(false);

function isPayable(o: OrderResponse): boolean {
  return o.status === ORDER_STATUS.PENDING || o.status === ORDER_STATUS.FAILED;
}

async function load(): Promise<void> {
  loading.value = true;
  loadError.value = "";
  try {
    orders.value = await consoleApi().listOrders();
  } catch (error) {
    loadError.value = (error as Error).message;
  } finally {
    loading.value = false;
  }
}

async function confirmCancel(): Promise<void> {
  if (!pendingCancel.value || cancelling.value) {
    return;
  }
  cancelling.value = true;
  try {
    await consoleApi().cancelOrder(pendingCancel.value.orderNo);
    showToast("success", "订单已取消");
    pendingCancel.value = null;
    await load();
  } catch (error) {
    showToast("error", (error as Error).message);
  } finally {
    cancelling.value = false;
  }
}

onMounted(load);
</script>

<template>
  <PageHead title="我的订单">
    <template #facts
      >共 <span class="fact">{{ orders.length }}</span> 笔</template
    >
  </PageHead>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="!loading && !loadError && orders.length === 0"
    empty-text="还没有订单"
  >
    <template #empty-action>
      <RouterLink :to="{ name: 'PLANS' }" class="admin-btn">去购买套餐</RouterLink>
    </template>
    <table class="admin-table">
      <thead>
        <tr>
          <th>订单号</th>
          <th>套餐</th>
          <th class="col-amount">金额</th>
          <th>状态</th>
          <th>创建时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="o in orders" :key="o.orderNo">
          <td class="fact">{{ o.orderNo }}</td>
          <td>
            {{ o.name }}
            <span class="pill muted">{{ agentLabel(o.agentType) }}</span>
          </td>
          <td class="col-amount fact">{{ formatAmount(o.amountMinor, o.planCurrency) }}</td>
          <td>
            <span class="pill" :class="{ muted: o.status !== ORDER_STATUS.PAID }">
              {{ ORDER_STATUS_LABELS[o.status] ?? o.status }}
            </span>
            <span v-if="o.status === ORDER_STATUS.PAID" class="muted order-note">
              · {{ o.subscriptionId === null ? "订阅建立中" : "订阅已建出，待管理员开通" }}
            </span>
          </td>
          <td class="fact muted">{{ formatDateTime(o.createdAt) }}</td>
          <td class="actions">
            <template v-if="isPayable(o)">
              <RouterLink :to="{ name: 'PAY', params: { orderNo: o.orderNo } }" class="admin-link"
                >去支付</RouterLink
              >
              <button type="button" class="admin-link danger" @click="pendingCancel = o">
                取消
              </button>
            </template>
          </td>
        </tr>
      </tbody>
    </table>
  </DataCard>

  <ConfirmDialog
    v-if="pendingCancel"
    title="取消订单"
    :message="`确认取消订单 ${pendingCancel.orderNo}？取消后可以重新购买。`"
    confirm-text="取消订单"
    :busy="cancelling"
    @confirm="confirmCancel"
    @cancel="pendingCancel = null"
  />
</template>
