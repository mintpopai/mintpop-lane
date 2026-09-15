<script setup lang="ts">
// 收银台：订单摘要 + 三张支付方式卡 + 确认区（二维码 / Payment Element）+ 取消。
// 逻辑照搬 mintpop-shop：微信 / 支付宝桌面端本地画二维码后每 2 秒 verify，银行卡 succeeded 直接去结果页
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { Stripe, StripeElements } from "@stripe/stripe-js";
import { consoleApi } from "../api";
import type { PaymentIntentInfo } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import PageHead from "../components/PageHead.vue";
import { buildPayOptions, isPaidStatus, type PayOption, type StripeSubMethod } from "../payment";
import {
  confirmCardPayment,
  createCardElements,
  getStripe,
  startAlipay,
  startWechatPay,
} from "../stripe";
import { showToast } from "../toast";
import { formatAmount } from "../utils/format";

/** 二维码有效期（秒）：Stripe 不在 next_action 里下发过期时间，按微信码常见时效取 15 分钟 */
const QR_TTL_SECONDS = 15 * 60;

const route = useRoute();
const router = useRouter();
const orderNo = route.params.orderNo as string;
const returnUrl = `${location.origin}/payment/result?order_no=${orderNo}`;

const loading = ref(true);
const loadError = ref("");
const intentInfo = ref<PaymentIntentInfo | null>(null);
const payOptions = ref<PayOption[]>([]);
const selectedKey = ref("");
const submitting = ref(false);
const polling = ref(false);

const orderSecondsLeft = ref(0);
const orderExpired = ref(false);

const cancelDialogOpen = ref(false);
const cancelBusy = ref(false);

const qrFor = ref<StripeSubMethod | null>(null);
const qrCanvas = ref<HTMLCanvasElement | null>(null);
const qrSecondsLeft = ref(0);

const cardMount = ref<HTMLDivElement | null>(null);
let stripe: Stripe | null = null;
let cardElements: StripeElements | null = null;
let pollTimer: ReturnType<typeof setInterval> | undefined;
let qrTimer: ReturnType<typeof setInterval> | undefined;
let orderTimer: ReturnType<typeof setInterval> | undefined;

const selectedOption = computed(
  () => payOptions.value.find((o) => o.key === selectedKey.value) ?? null,
);
const isCardSelected = computed(() => selectedOption.value?.subMethod === "card");

const METHOD_NAMES: Record<StripeSubMethod, string> = {
  wxpay: "微信支付",
  alipay: "支付宝",
  card: "银行卡",
};

function formatCountdown(total: number): string {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}
const qrCountdown = computed(() => formatCountdown(qrSecondsLeft.value));
const orderCountdown = computed(() => formatCountdown(orderSecondsLeft.value));

onMounted(async () => {
  try {
    const [checkout, intent] = await Promise.all([
      consoleApi().checkoutInfo(),
      consoleApi().createPaymentIntent(orderNo),
    ]);
    intentInfo.value = intent;
    payOptions.value = buildPayOptions(checkout.methods);
    selectedKey.value = payOptions.value[0]?.key ?? "";
    if (payOptions.value.length === 0 || !checkout.stripePublishableKey) {
      loadError.value = "当前无法支付：支付暂未开放";
      return;
    }
    try {
      stripe = await getStripe(checkout.stripePublishableKey);
    } catch {
      stripe = null;
    }
    if (!stripe) {
      // Stripe.js 只能从 js.stripe.com 加载，网络不通时明确告诉用户，而不是留一个点不动的按钮
      loadError.value = "支付组件加载失败，请检查网络后刷新重试";
      return;
    }
    startOrderCountdown(intent.expireRemainingSeconds);
  } catch (e) {
    loadError.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  clearInterval(pollTimer);
  clearInterval(qrTimer);
  clearInterval(orderTimer);
});

/** 订单级支付时限：到点主动 verify 触发后端懒惰过期；若其实已支付则去结果页 */
function startOrderCountdown(seconds: number): void {
  orderSecondsLeft.value = Math.max(0, Math.floor(seconds));
  if (orderSecondsLeft.value === 0) {
    void onOrderDeadline();
    return;
  }
  orderTimer = setInterval(() => {
    orderSecondsLeft.value -= 1;
    if (orderSecondsLeft.value <= 0) {
      clearInterval(orderTimer);
      void onOrderDeadline();
    }
  }, 1000);
}

async function onOrderDeadline(): Promise<void> {
  try {
    const result = await consoleApi().verifyOrder(orderNo);
    if (isPaidStatus(result.status)) {
      goResult();
      return;
    }
  } catch {
    // 触发失败不影响前端切过期态：后端任一入口读到该单时仍会过期它
  }
  showExpiredState();
}

function showExpiredState(): void {
  clearInterval(orderTimer);
  clearInterval(qrTimer);
  clearInterval(pollTimer);
  polling.value = false;
  qrFor.value = null;
  cancelDialogOpen.value = false;
  orderExpired.value = true;
}

/** 切换方式：收起二维码并停掉轮询；选中银行卡时挂 Payment Element */
watch(selectedKey, async () => {
  qrFor.value = null;
  clearInterval(qrTimer);
  // 隐藏的二维码不该继续每 2 秒打 verifyOrder；同时清掉 polling 标记，
  // 免得用户换支付方式后再次发起支付时被这个陈旧标记短路，压根不进 startPolling
  clearInterval(pollTimer);
  polling.value = false;
  if (isCardSelected.value && intentInfo.value && stripe && !cardElements) {
    cardElements = createCardElements(stripe, {
      amount: intentInfo.value.amountMinor,
      currency: intentInfo.value.currency,
    });
    cardElements.create("payment", { layout: "tabs" });
    await Promise.resolve();
    if (cardMount.value) {
      cardElements.getElement("payment")?.mount(cardMount.value);
    }
  }
});

function onRadioKeydown(event: KeyboardEvent): void {
  const keys = ["ArrowRight", "ArrowDown", "ArrowLeft", "ArrowUp"];
  if (!keys.includes(event.key)) {
    return;
  }
  event.preventDefault();
  const idx = payOptions.value.findIndex((o) => o.key === selectedKey.value);
  const delta = event.key === "ArrowRight" || event.key === "ArrowDown" ? 1 : -1;
  const next = (idx + delta + payOptions.value.length) % payOptions.value.length;
  selectedKey.value = payOptions.value[next].key;
  document.getElementById(`pay-option-${payOptions.value[next].key}`)?.focus();
}

async function confirm(): Promise<void> {
  const option = selectedOption.value;
  if (!option || !stripe || !intentInfo.value || submitting.value) {
    return;
  }
  submitting.value = true;
  try {
    // 支付前先核实：页面可能已挂很久，超时 / 已取消的单不放行
    try {
      const check = await consoleApi().verifyOrder(orderNo);
      if (isPaidStatus(check.status)) {
        goResult();
        return;
      }
      if (check.status === "EXPIRED") {
        showExpiredState();
        return;
      }
      if (check.status === "CANCELLED") {
        showToast("error", "订单已取消");
        await router.push({ name: "ORDERS" });
        return;
      }
    } catch {
      // 核实失败（网络抖动）不阻断支付：后端与 Stripe 侧仍有各自的拦截
    }
    const clientSecret = intentInfo.value.clientSecret;
    if (option.subMethod === "wxpay") {
      const qr = await startWechatPay(stripe, clientSecret);
      await showQr("wxpay", qr);
      startPolling();
    } else if (option.subMethod === "alipay") {
      const { qrUrl } = await startAlipay(stripe, clientSecret, returnUrl);
      if (qrUrl) {
        await showQr("alipay", qrUrl);
        startPolling();
      }
    } else {
      if (!cardElements) {
        return;
      }
      const status = await confirmCardPayment(stripe, cardElements, clientSecret, returnUrl);
      if (status === "succeeded") {
        goResult();
      } else {
        startPolling();
      }
    }
  } catch (e) {
    showToast("error", (e as Error).message || "支付发起失败");
  } finally {
    submitting.value = false;
  }
}

async function showQr(kind: StripeSubMethod, content: string): Promise<void> {
  qrFor.value = kind;
  qrSecondsLeft.value = QR_TTL_SECONDS;
  clearInterval(qrTimer);
  qrTimer = setInterval(() => {
    if (qrSecondsLeft.value > 0) {
      qrSecondsLeft.value -= 1;
    } else {
      clearInterval(qrTimer);
    }
  }, 1000);
  await Promise.resolve();
  if (qrCanvas.value) {
    const { toCanvas } = await import("qrcode");
    await toCanvas(qrCanvas.value, content, { width: 200, margin: 1 });
  }
}

/** 每 2 秒核实一次：PAID 跳结果页；过期 / 取消也是终态 */
function startPolling(): void {
  if (polling.value) {
    return;
  }
  polling.value = true;
  pollTimer = setInterval(async () => {
    try {
      const result = await consoleApi().verifyOrder(orderNo);
      if (isPaidStatus(result.status)) {
        clearInterval(pollTimer);
        polling.value = false;
        goResult();
      } else if (result.status === "EXPIRED") {
        showExpiredState();
      } else if (result.status === "CANCELLED") {
        clearInterval(pollTimer);
        polling.value = false;
        showToast("error", "订单已取消");
        await router.push({ name: "ORDERS" });
      }
    } catch {
      // 轮询失败不打断，下一轮再试
    }
  }, 2000);
}

function goResult(): void {
  void router.push({ name: "PAYMENT_RESULT", query: { order_no: orderNo } });
}

async function onCancel(): Promise<void> {
  if (cancelBusy.value) {
    return;
  }
  cancelBusy.value = true;
  try {
    await consoleApi().cancelOrder(orderNo);
    cancelDialogOpen.value = false;
    showToast("success", "订单已取消");
    await router.push({ name: "ORDERS" });
  } catch (e) {
    showToast("error", (e as Error).message);
  } finally {
    cancelBusy.value = false;
  }
}
</script>

<template>
  <PageHead title="支付" />

  <p v-if="loading" class="admin-hint">加载中……</p>
  <div v-else-if="loadError" class="admin-card">
    <p class="admin-hint error">
      {{ loadError }}
      <RouterLink :to="{ name: 'ORDERS' }" class="admin-link">查看我的订单</RouterLink>
    </p>
  </div>

  <template v-else-if="intentInfo">
    <section class="admin-card pay-summary">
      <div class="pay-row">
        <span class="pay-product">{{ intentInfo.productName }}</span>
        <span class="pay-amount fact">{{
          formatAmount(intentInfo.amountMinor, intentInfo.currency)
        }}</span>
      </div>
      <div class="pay-row muted">
        <span
          >订单号 <span class="fact">{{ intentInfo.orderNo }}</span></span
        >
        <span v-if="!orderExpired && orderSecondsLeft > 0" class="pay-deadline"
          >请在 {{ orderCountdown }} 内完成支付</span
        >
      </div>
    </section>

    <div v-if="orderExpired" class="admin-card">
      <p class="admin-hint error">
        订单已过期，请重新购买。
        <RouterLink :to="{ name: 'ORDERS' }" class="admin-link">查看我的订单</RouterLink>
      </p>
    </div>

    <section v-else class="admin-card pay-panel">
      <div class="pay-panel-head">
        <h3 class="pay-section-label">支付方式</h3>
        <span class="muted pay-powered">由 <b class="stripe-word">Stripe</b> 提供支付服务</span>
      </div>

      <div class="method-grid" role="radiogroup" aria-label="支付方式" @keydown="onRadioKeydown">
        <button
          v-for="option in payOptions"
          :id="`pay-option-${option.key}`"
          :key="option.key"
          type="button"
          role="radio"
          class="method-card"
          :class="{ selected: option.key === selectedKey }"
          :aria-checked="option.key === selectedKey"
          :tabindex="option.key === selectedKey ? 0 : -1"
          @click="selectedKey = option.key"
        >
          <!-- 图标一律内联 SVG / 文字，不引外链图片 -->
          <span
            v-if="option.subMethod === 'wxpay'"
            class="method-icon icon-wxpay"
            aria-hidden="true"
          >
            <svg viewBox="0 0 24 24" width="22" height="22" fill="#ffffff">
              <path
                d="M9.5 4C5.9 4 3 6.5 3 9.6c0 1.8 1 3.4 2.5 4.4l-.6 2 2.2-1.2c.6.2 1.3.3 2 .3h.3A5.3 5.3 0 0 1 9 13c0-2.9 2.8-5.2 6.2-5.2h.4C15 5.6 12.5 4 9.5 4Zm-2.2 2.9a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8Zm4.6 0a.9.9 0 1 1 0 1.8.9.9 0 0 1 0-1.8ZM15.2 9c-3 0-5.4 1.9-5.4 4.3 0 2.4 2.4 4.3 5.4 4.3.6 0 1.2-.1 1.7-.2l1.9 1-.5-1.7c1.4-.8 2.3-2 2.3-3.4C20.6 10.9 18.2 9 15.2 9Zm-1.9 2.4a.8.8 0 1 1 0 1.6.8.8 0 0 1 0-1.6Zm3.8 0a.8.8 0 1 1 0 1.6.8.8 0 0 1 0-1.6Z"
              />
            </svg>
          </span>
          <span
            v-else-if="option.subMethod === 'alipay'"
            class="method-icon icon-alipay"
            aria-hidden="true"
            >支</span
          >
          <span v-else class="method-icon icon-card" aria-hidden="true">
            <svg
              viewBox="0 0 24 24"
              width="22"
              height="22"
              fill="none"
              stroke="#ffffff"
              stroke-width="2"
              stroke-linecap="round"
            >
              <rect x="2.5" y="5" width="19" height="14" rx="2.5" />
              <path d="M2.5 9.5h19" />
              <path d="M6 15h4" />
            </svg>
          </span>
          <span class="method-text">
            <span class="method-name">{{ METHOD_NAMES[option.subMethod] }}</span>
            <span class="method-desc">{{
              option.subMethod === "card" ? "Visa / Mastercard 等" : "扫码支付"
            }}</span>
          </span>
          <span class="radio-dot" aria-hidden="true"></span>
        </button>
      </div>

      <div v-show="isCardSelected && !qrFor" ref="cardMount" class="card-element"></div>
      <p v-if="polling && !qrFor" class="admin-hint pay-processing">正在确认支付结果…</p>

      <div v-if="qrFor" class="qr-box">
        <canvas ref="qrCanvas" width="200" height="200" class="qr-canvas"></canvas>
        <p class="qr-hint">{{ qrFor === "wxpay" ? "请用微信扫码支付" : "请用支付宝扫码支付" }}</p>
        <p v-if="qrSecondsLeft > 0" class="qr-expiry muted">二维码 {{ qrCountdown }} 后失效</p>
        <p v-else class="qr-expiry expired">
          二维码已失效
          <button type="button" class="admin-link" @click="confirm">重新生成</button>
        </p>
        <p v-if="polling" class="qr-hint muted">等待支付结果…</p>
      </div>

      <div class="pay-actions">
        <button
          v-if="!qrFor"
          type="button"
          class="admin-btn pay-btn"
          :disabled="submitting || polling || !selectedOption"
          @click="confirm"
        >
          {{ submitting ? "处理中…" : isCardSelected ? "确认付款" : "生成二维码" }}
        </button>
        <button type="button" class="admin-link pay-cancel" @click="cancelDialogOpen = true">
          取消订单
        </button>
      </div>

      <div class="security-note muted">
        <svg
          viewBox="0 0 24 24"
          width="16"
          height="16"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M12 3l7 3v5c0 4.4-3 8.3-7 9.5C8 19.3 5 15.4 5 11V6l7-3Z" />
          <path d="M9 11.5l2 2 4-4" />
        </svg>
        <span>支付信息由 <b class="stripe-word">Stripe</b> 加密处理，我们不接触你的卡号</span>
      </div>
    </section>
  </template>

  <ConfirmDialog
    v-if="cancelDialogOpen"
    title="取消订单"
    :message="`确认取消订单 ${orderNo}？取消后可以重新购买。`"
    confirm-text="取消订单"
    :busy="cancelBusy"
    @confirm="onCancel"
    @cancel="cancelDialogOpen = false"
  />
</template>
