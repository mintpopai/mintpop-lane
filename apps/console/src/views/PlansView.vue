<script setup lang="ts">
// 购买套餐：一级 tab 按 agent 类型分，卡片一行一个套餐；点购买即建单跳支付页，中间不填任何东西
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { consoleApi } from "../api";
import type { PlanResponse } from "../api/types";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import ViewTabs from "../components/ViewTabs.vue";
import { showToast } from "../toast";
import { agentTabs, plansForAgent } from "../utils/plans";

const router = useRouter();

const loading = ref(true);
const loadError = ref("");
const plans = ref<PlanResponse[]>([]);
const agentType = ref<string | null>(null);
/** 支付未配置时（checkout-info 的 methods 为空）购买入口整体禁用，别让人下单后卡在支付页 */
const paymentOpen = ref(true);
const buyingPlanId = ref<number | null>(null);

const tabs = computed(() => agentTabs(plans.value));
const visiblePlans = computed(() => plansForAgent(plans.value, agentType.value));

onMounted(async () => {
  // 两个请求分开处理失败：支付状态只是「能不能买」的附加信息，它查询失败不该连累
  // 已经拿到的套餐列表——只禁用购买按钮并提示，列表仍可浏览；套餐列表本身查询失败才是真的没得看
  const [plansResult, checkoutResult] = await Promise.allSettled([
    consoleApi().listPlans(),
    consoleApi().checkoutInfo(),
  ]);

  if (plansResult.status === "fulfilled") {
    plans.value = plansResult.value;
    agentType.value = tabs.value[0]?.value ?? null;
  } else {
    loadError.value = (plansResult.reason as Error).message;
  }

  if (checkoutResult.status === "fulfilled") {
    paymentOpen.value = checkoutResult.value.methods.length > 0;
  } else {
    paymentOpen.value = false;
    showToast("error", (checkoutResult.reason as Error).message);
  }

  loading.value = false;
});

async function buy(plan: PlanResponse): Promise<void> {
  if (buyingPlanId.value !== null) {
    return;
  }
  buyingPlanId.value = plan.id;
  try {
    const order = await consoleApi().createOrder(plan.id);
    await router.push({ name: "PAY", params: { orderNo: order.orderNo } });
  } catch (error) {
    showToast("error", (error as Error).message);
  } finally {
    buyingPlanId.value = null;
  }
}
</script>

<template>
  <PageHead title="购买套餐">
    <template #facts>
      共 <span class="fact">{{ plans.length }}</span> 个套餐
      <span v-if="!paymentOpen && !loading">· 支付暂未开放，请稍后再来</span>
    </template>
  </PageHead>

  <ViewTabs v-if="tabs.length > 1" v-model="agentType" :options="tabs" label="按 Agent 类型" />

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="!loading && !loadError && visiblePlans.length === 0"
    empty-text="暂无可购买的套餐"
  >
    <ul class="plan-list">
      <li v-for="plan in visiblePlans" :key="plan.id" class="plan-card">
        <!-- 没有图就整块不占位：留一个空框比没有更显得缺东西 -->
        <div v-if="plan.imageUrl" class="plan-thumb">
          <img :src="plan.imageUrl" alt="" />
        </div>
        <div class="plan-text">
          <span class="plan-name">{{ plan.name }}</span>
          <span v-if="plan.description" class="plan-desc">{{ plan.description }}</span>
          <span class="plan-spec"
            ><span class="fact">{{ plan.durationDays }}</span> 天</span
          >
        </div>
        <span class="plan-price fact">{{ plan.price.toFixed(2) }} {{ plan.currency }}</span>
        <button
          type="button"
          class="admin-btn"
          :disabled="!paymentOpen || buyingPlanId !== null"
          :title="paymentOpen ? undefined : '支付暂未开放'"
          @click="buy(plan)"
        >
          {{ buyingPlanId === plan.id ? "下单中…" : "购买" }}
        </button>
      </li>
    </ul>
  </DataCard>
</template>
