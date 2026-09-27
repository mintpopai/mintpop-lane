import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { OrderResponse } from "../api/types";
import { showToast } from "../toast";
import OrdersView from "./OrdersView.vue";

const listOrders = vi.fn<() => Promise<OrderResponse[]>>();
const cancelOrder = vi.fn<(orderNo: string) => Promise<void>>();

vi.mock("../api", () => ({ consoleApi: () => ({ listOrders, cancelOrder }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function order(overrides: Partial<OrderResponse> = {}): OrderResponse {
  return {
    orderNo: "LN20260915083005123456",
    name: "Claude 月付",
    agentType: "CLAUDE",
    planDurationDays: 30,
    planPrice: 99.99,
    planCurrency: "USD",
    amountMinor: 9999,
    status: "PENDING",
    paidAt: null,
    subscriptionId: null,
    assignmentNo: null,
    createdAt: "2026-09-15T08:30:05Z",
    ...overrides,
  };
}

const stubs = {
  RouterLink: { props: ["to"], template: "<a :data-to='JSON.stringify(to)'><slot /></a>" },
};

describe("OrdersView", () => {
  beforeEach(() => vi.clearAllMocks());

  it("列表：订单号等宽、套餐、金额、状态中文；待支付行有「去支付」与「取消」", async () => {
    listOrders.mockResolvedValue([order()]);
    const wrapper = mount(OrdersView, { global: { stubs }, attachTo: document.body });
    await flushPromises();
    const row = wrapper.get("tbody tr");
    expect(row.get(".fact").text()).toBe("LN20260915083005123456");
    expect(row.text()).toContain("Claude 月付");
    expect(row.text()).toContain("99.99 USD");
    expect(row.text()).toContain("待支付");
    const pay = row.get("a");
    expect(pay.text()).toBe("去支付");
    expect(pay.attributes("data-to")).toContain("LN20260915083005123456");
    expect(row.findAll("button").map((b) => b.text())).toContain("取消");
    wrapper.unmount();
  });

  it("已支付行显示已开通 / 待开通去向，没有操作按钮", async () => {
    listOrders.mockResolvedValue([
      order({
        status: "PAID",
        paidAt: "2026-09-15T08:35:00Z",
        subscriptionId: 9,
        assignmentNo: "7K3M9QX2FT",
      }),
    ]);
    const wrapper = mount(OrdersView, { global: { stubs } });
    await flushPromises();
    const row = wrapper.get("tbody tr");
    expect(row.text()).toContain("已支付");
    expect(row.text()).toContain("订阅 7K3M9-QX2FT 待开通");
    expect(row.findAll("button")).toHaveLength(0);
  });

  it("取消需二次确认，确认后调接口并重拉列表", async () => {
    listOrders
      .mockResolvedValueOnce([order()])
      .mockResolvedValueOnce([order({ status: "CANCELLED" })]);
    cancelOrder.mockResolvedValue();
    const wrapper = mount(OrdersView, { global: { stubs }, attachTo: document.body });
    await flushPromises();
    await wrapper.get("tbody tr button").trigger("click");
    await flushPromises();
    expect(cancelOrder).not.toHaveBeenCalled();
    const confirm = Array.from(document.querySelectorAll("button")).find(
      (b) => b.textContent?.trim() === "取消订单",
    );
    confirm?.click();
    await flushPromises();
    expect(cancelOrder).toHaveBeenCalledWith("LN20260915083005123456");
    expect(showToast).toHaveBeenCalledWith("success", "订单已取消");
    expect(wrapper.get("tbody tr").text()).toContain("已取消");
    wrapper.unmount();
  });

  it("没有订单时是空态", async () => {
    listOrders.mockResolvedValue([]);
    const wrapper = mount(OrdersView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.text()).toContain("还没有订单");
  });
});
