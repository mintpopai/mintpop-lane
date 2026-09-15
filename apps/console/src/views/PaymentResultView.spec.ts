import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { VerifyOrderResponse } from "../api/types";
import PaymentResultView from "./PaymentResultView.vue";

const verifyOrder = vi.fn<(orderNo: string) => Promise<VerifyOrderResponse>>();
const replace = vi.fn();
let query: Record<string, string> = {};

vi.mock("../api", () => ({ consoleApi: () => ({ verifyOrder }) }));
vi.mock("vue-router", () => ({
  useRoute: () => ({ query }),
  useRouter: () => ({ replace }),
}));

const stubs = { RouterLink: { template: "<a><slot /></a>" } };

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  query = { order_no: "LN1", payment_intent: "pi_1", redirect_status: "succeeded" };
});

afterEach(() => vi.useRealTimers());

describe("PaymentResultView", () => {
  it("进页先清掉 Stripe 追加的 query，只留 order_no", async () => {
    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "PENDING" });
    mount(PaymentResultView, { global: { stubs } });
    await flushPromises();
    expect(replace).toHaveBeenCalledWith({ name: "PAYMENT_RESULT", query: { order_no: "LN1" } });
  });

  it("PAID 即成功，文案告诉用户管理员开通后去「我的订阅」看", async () => {
    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "PAID" });
    const w = mount(PaymentResultView, { global: { stubs } });
    await flushPromises();
    expect(w.get(".result-status").text()).toBe("支付成功");
    expect(w.text()).toContain("管理员开通后");
  });

  it("FAILED / CANCELLED / EXPIRED 为失败态", async () => {
    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "EXPIRED" });
    const w = mount(PaymentResultView, { global: { stubs } });
    await flushPromises();
    expect(w.get(".result-status").text()).toBe("支付未完成");
  });

  it("最多 15 次 × 2 秒仍未确认转「结果待确认」，而不是报失败", async () => {
    verifyOrder.mockResolvedValue({ orderNo: "LN1", status: "PENDING" });
    const w = mount(PaymentResultView, { global: { stubs } });
    await flushPromises();
    await vi.advanceTimersByTimeAsync(2000 * 15);
    await flushPromises();
    expect(verifyOrder).toHaveBeenCalledTimes(15);
    expect(w.get(".result-status").text()).toBe("结果待确认");
  });

  it("终态守卫：定格成功后，迟到的响应不会翻转结果", async () => {
    let resolveSlow: (v: VerifyOrderResponse) => void = () => {};
    verifyOrder
      .mockImplementationOnce(() => new Promise((r) => (resolveSlow = r)))
      .mockResolvedValueOnce({ orderNo: "LN1", status: "PAID" });
    const w = mount(PaymentResultView, { global: { stubs } });
    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();
    expect(w.get(".result-status").text()).toBe("支付成功");
    resolveSlow({ orderNo: "LN1", status: "EXPIRED" });
    await flushPromises();
    expect(w.get(".result-status").text()).toBe("支付成功");
  });

  it("没有 order_no 直接进「结果待确认」", async () => {
    query = {};
    const w = mount(PaymentResultView, { global: { stubs } });
    await flushPromises();
    expect(w.get(".result-status").text()).toBe("结果待确认");
    expect(verifyOrder).not.toHaveBeenCalled();
  });
});
