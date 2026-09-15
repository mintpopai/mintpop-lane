<script setup lang="ts">
// 支付结果：整页跳转回流（支付宝移动端、3DS）与站内跳转都落这里，最多 15 次 × 2 秒确认
import { onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { consoleApi } from "../api";
import PageHead from "../components/PageHead.vue";
import { isPaidStatus } from "../payment";

const MAX_ATTEMPTS = 15;

type Stage = "confirming" | "success" | "pending" | "failed";

const route = useRoute();
const router = useRouter();
const stage = ref<Stage>("confirming");
const orderNo = String(route.query.order_no ?? "");
let timer: ReturnType<typeof setInterval> | undefined;
let attempts = 0;

onMounted(() => {
  // 清掉 Stripe 回跳追加的 query（payment_intent / redirect_status），只留 order_no
  void router.replace({ name: "PAYMENT_RESULT", query: orderNo ? { order_no: orderNo } : {} });
  if (!orderNo) {
    stage.value = "pending";
    return;
  }
  timer = setInterval(check, 2000);
  void check();
});

onUnmounted(() => clearInterval(timer));

async function check(): Promise<void> {
  if (stage.value !== "confirming") {
    return;
  }
  attempts += 1;
  try {
    const result = await consoleApi().verifyOrder(orderNo);
    if (isPaidStatus(result.status)) {
      finish("success");
    } else if (
      result.status === "FAILED" ||
      result.status === "CANCELLED" ||
      result.status === "EXPIRED"
    ) {
      finish("failed");
    } else if (attempts >= MAX_ATTEMPTS) {
      finish("pending");
    }
  } catch {
    if (attempts >= MAX_ATTEMPTS) {
      finish("pending");
    }
  }
}

/** 终态守卫：只允许从 confirming 迁出一次，迟到的慢响应不得翻转已定格的结果 */
function finish(next: Stage): void {
  if (stage.value !== "confirming") {
    return;
  }
  clearInterval(timer);
  stage.value = next;
}
</script>

<template>
  <PageHead title="支付结果" />
  <div class="admin-card result-card" aria-live="polite">
    <template v-if="stage === 'confirming'">
      <p class="result-status">正在确认支付结果…</p>
    </template>
    <template v-else-if="stage === 'success'">
      <p class="result-status success">支付成功</p>
      <p class="muted">订阅已建出，管理员开通后可在「我的订阅」查看起止时间。</p>
    </template>
    <template v-else-if="stage === 'pending'">
      <p class="result-status">结果待确认</p>
      <p class="muted">支付结果尚未同步。如已扣款请稍后在「我的订单」查看，无需重复支付。</p>
    </template>
    <template v-else>
      <p class="result-status failed">支付未完成</p>
      <p class="muted">订单未支付成功，可以在「我的订单」里继续支付或重新购买。</p>
    </template>
    <nav v-if="stage !== 'confirming'" class="result-links">
      <RouterLink :to="{ name: 'SUBSCRIPTIONS' }" class="admin-btn">我的订阅</RouterLink>
      <RouterLink :to="{ name: 'ORDERS' }" class="admin-btn-ghost">我的订单</RouterLink>
    </nav>
  </div>
</template>
