import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CheckoutInfo, PaymentIntentInfo, VerifyOrderResponse } from "../api/types";
import {
  confirmCardPayment,
  createCardElements,
  getStripe,
  startAlipay,
  startWechatPay,
} from "../stripe";
import { showToast } from "../toast";
import PayView from "./PayView.vue";

const checkoutInfo = vi.fn<() => Promise<CheckoutInfo>>();
const createPaymentIntent = vi.fn<(orderNo: string) => Promise<PaymentIntentInfo>>();
const verifyOrder = vi.fn<(orderNo: string) => Promise<VerifyOrderResponse>>();
const cancelOrder = vi.fn<(orderNo: string) => Promise<void>>();
const push = vi.fn();

vi.mock("../api", () => ({
  consoleApi: () => ({ checkoutInfo, createPaymentIntent, verifyOrder, cancelOrder }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));
vi.mock("vue-router", () => ({
  useRoute: () => ({ params: { orderNo: "LN1" } }),
  useRouter: () => ({ push }),
}));
vi.mock("../stripe", () => ({
  getStripe: vi.fn(),
  startWechatPay: vi.fn(),
  startAlipay: vi.fn(),
  createCardElements: vi.fn(),
  confirmCardPayment: vi.fn(),
}));
vi.mock("qrcode", () => ({ toCanvas: vi.fn().mockResolvedValue(undefined) }));

const getStripeMock = vi.mocked(getStripe);
const startWechatPayMock = vi.mocked(startWechatPay);
const startAlipayMock = vi.mocked(startAlipay);
const createCardElementsMock = vi.mocked(createCardElements);
const confirmCardPaymentMock = vi.mocked(confirmCardPayment);

const stubs = { RouterLink: { template: "<a><slot /></a>" } };
let wrapper: VueWrapper | null = null;

function intent(overrides: Partial<PaymentIntentInfo> = {}): PaymentIntentInfo {
  return {
    orderNo: "LN1",
    clientSecret: "cs_1",
    amountMinor: 9999,
    currency: "USD",
    productName: "Claude 月付",
    expireRemainingSeconds: 1800,
    ...overrides,
  };
}

async function mountPay(
  options: { checkout?: Partial<CheckoutInfo>; intent?: Partial<PaymentIntentInfo> } = {},
) {
  checkoutInfo.mockResolvedValue({
    methods: ["stripe"],
    stripePublishableKey: "pk",
    ...options.checkout,
  });
  createPaymentIntent.mockResolvedValue(intent(options.intent));
  getStripeMock.mockResolvedValue({} as Awaited<ReturnType<typeof getStripe>>);
  wrapper = mount(PayView, { global: { stubs }, attachTo: document.body });
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  createCardElementsMock.mockReturnValue({
    create: vi.fn(),
    getElement: vi.fn().mockReturnValue({ mount: vi.fn() }),
  } as unknown as ReturnType<typeof createCardElements>);
  verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "PENDING" });
});

afterEach(() => {
  wrapper?.unmount();
  wrapper = null;
  document.body.innerHTML = "";
  vi.useRealTimers();
});

describe("收银台加载", () => {
  it("展示套餐名、金额、订单号与拍平的三个支付方式，默认选中微信", async () => {
    const w = await mountPay();
    expect(w.get(".pay-product").text()).toBe("Claude 月付");
    expect(w.get(".pay-amount").text()).toBe("99.99 USD");
    expect(w.text()).toContain("LN1");
    const cards = w.findAll(".method-card");
    expect(cards).toHaveLength(3);
    expect(cards[0].attributes("aria-checked")).toBe("true");
    expect(cards.map((c) => c.get(".method-name").text())).toEqual([
      "微信支付",
      "支付宝",
      "银行卡",
    ]);
  });

  it("后端没开通道或没给公钥时判为不可支付", async () => {
    const w = await mountPay({ checkout: { methods: [] } });
    expect(w.text()).toContain("当前无法支付");
    expect(w.find(".method-card").exists()).toBe(false);
  });

  it("加载失败时展示服务端说法与回订单列表的出口", async () => {
    checkoutInfo.mockResolvedValue({ methods: ["stripe"], stripePublishableKey: "pk" });
    createPaymentIntent.mockRejectedValue(new Error("订单当前不可支付"));
    wrapper = mount(PayView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.get(".admin-hint.error").text()).toContain("订单当前不可支付");
    expect(wrapper.text()).toContain("查看我的订单");
  });

  it("Stripe.js 加载失败给出明确提示而不是白屏", async () => {
    checkoutInfo.mockResolvedValue({ methods: ["stripe"], stripePublishableKey: "pk" });
    createPaymentIntent.mockResolvedValue(intent());
    getStripeMock.mockRejectedValue(new Error("network"));
    wrapper = mount(PayView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.text()).toContain("支付组件加载失败");
  });
});

describe("倒计时与过期", () => {
  it("按剩余秒数渲染 mm:ss，归零时主动 verify；未支付则切过期态", async () => {
    const w = await mountPay({ intent: { expireRemainingSeconds: 2 } });
    expect(w.get(".pay-deadline").text()).toContain("00:02");
    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "EXPIRED" });
    await vi.advanceTimersByTimeAsync(2100);
    await flushPromises();
    expect(verifyOrder).toHaveBeenCalled();
    expect(w.text()).toContain("订单已过期");
    expect(w.find(".method-card").exists()).toBe(false);
  });

  it("归零时若其实已支付，直接去结果页", async () => {
    await mountPay({ intent: { expireRemainingSeconds: 1 } });
    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "PAID" });
    await vi.advanceTimersByTimeAsync(1100);
    await flushPromises();
    expect(push).toHaveBeenCalledWith({ name: "PAYMENT_RESULT", query: { order_no: "LN1" } });
  });
});

describe("确认支付", () => {
  it("微信：先 verify 一次，再取二维码本地渲染并每 2 秒轮询，PAID 后跳结果页", async () => {
    const w = await mountPay();
    startWechatPayMock.mockResolvedValue("weixin://wxpay/x");
    await w.get(".pay-btn").trigger("click");
    await flushPromises();
    expect(verifyOrder).toHaveBeenCalledTimes(1);
    expect(startWechatPayMock).toHaveBeenCalledWith(expect.anything(), "cs_1");
    expect(w.find(".qr-canvas").exists()).toBe(true);

    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "PAID" });
    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(push).toHaveBeenCalledWith({ name: "PAYMENT_RESULT", query: { order_no: "LN1" } });
  });

  it("支付宝桌面端拿托管页 URL 画二维码", async () => {
    const w = await mountPay();
    await w.findAll(".method-card")[1].trigger("click");
    startAlipayMock.mockResolvedValue({ qrUrl: "https://alipay/x" });
    await w.get(".pay-btn").trigger("click");
    await flushPromises();
    expect(startAlipayMock).toHaveBeenCalledWith(
      expect.anything(),
      "cs_1",
      `${location.origin}/payment/result?order_no=LN1`,
    );
    expect(w.find(".qr-canvas").exists()).toBe(true);
  });

  it("银行卡：选中即挂 Payment Element，确认后 succeeded 直接去结果页", async () => {
    const w = await mountPay();
    await w.findAll(".method-card")[2].trigger("click");
    await flushPromises();
    expect(createCardElementsMock).toHaveBeenCalledWith(expect.anything(), {
      amount: 9999,
      currency: "USD",
    });
    confirmCardPaymentMock.mockResolvedValue("succeeded");
    await w.get(".pay-btn").trigger("click");
    await flushPromises();
    expect(push).toHaveBeenCalledWith({ name: "PAYMENT_RESULT", query: { order_no: "LN1" } });
  });

  it("银行卡：确认后非 succeeded 转入处理中，按钮禁用并给出反馈，PAID 后跳结果页", async () => {
    const w = await mountPay();
    await w.findAll(".method-card")[2].trigger("click");
    await flushPromises();
    confirmCardPaymentMock.mockResolvedValue("processing");
    await w.get(".pay-btn").trigger("click");
    await flushPromises();
    expect(w.find(".pay-processing").exists()).toBe(true);
    expect(w.get(".pay-btn").attributes("disabled")).toBeDefined();

    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "PAID" });
    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(push).toHaveBeenCalledWith({ name: "PAYMENT_RESULT", query: { order_no: "LN1" } });
  });

  it("微信二维码展示后锁定支付方式切换", async () => {
    const w = await mountPay();
    startWechatPayMock.mockResolvedValue("weixin://wxpay/x");
    await w.get(".pay-btn").trigger("click");
    await flushPromises();
    expect(w.find(".qr-canvas").exists()).toBe(true);

    const cards = w.findAll(".method-card");
    expect(cards[1].attributes("disabled")).toBeDefined();
    expect(cards[2].attributes("disabled")).toBeDefined();

    await cards[1].trigger("click");
    await flushPromises();
    expect(cards[0].attributes("aria-checked")).toBe("true");
    expect(w.get(".pay-locked").text()).toContain("微信支付");
  });

  it("二维码倒计时不超订单剩余时限", async () => {
    const w = await mountPay({ intent: { expireRemainingSeconds: 120 } });
    startWechatPayMock.mockResolvedValue("weixin://wxpay/x");
    await w.get(".pay-btn").trigger("click");
    await flushPromises();
    expect(w.get(".qr-expiry").text()).toContain("02:00");
  });

  it("确认前 verify 发现已取消：提示并回订单列表，不再发起支付", async () => {
    const w = await mountPay();
    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "CANCELLED" });
    await w.get(".pay-btn").trigger("click");
    await flushPromises();
    expect(startWechatPayMock).not.toHaveBeenCalled();
    expect(push).toHaveBeenCalledWith({ name: "ORDERS" });
  });

  it("Stripe 报错原样 toast", async () => {
    const w = await mountPay();
    startWechatPayMock.mockRejectedValue(new Error("二维码生成失败"));
    await w.get(".pay-btn").trigger("click");
    await flushPromises();
    expect(showToast).toHaveBeenCalledWith("error", "二维码生成失败");
  });
});

describe("取消订单", () => {
  it("需二次确认，确认后调接口并回订单列表", async () => {
    const w = await mountPay();
    cancelOrder.mockResolvedValue();
    await w.get(".pay-cancel").trigger("click");
    await flushPromises();
    // 页面上的「取消订单」按钮与弹窗内的确认按钮文案相同，用 .dialog 把查询范围收窄到弹窗内，
    // 避免 document 顺序下先命中背后那颗同名按钮（brief 原文按钮文案未区分，属测试自身缺陷的最小修复）
    const confirm = Array.from(document.querySelectorAll<HTMLButtonElement>(".dialog button")).find(
      (b) => b.textContent?.trim() === "取消订单",
    );
    confirm?.click();
    await flushPromises();
    expect(cancelOrder).toHaveBeenCalledWith("LN1");
    expect(push).toHaveBeenCalledWith({ name: "ORDERS" });
  });
});
