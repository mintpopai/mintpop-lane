import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CheckoutInfo, OrderCreateResponse, PlanResponse } from "../api/types";
import { showToast } from "../toast";
import PlansView from "./PlansView.vue";

const listPlans = vi.fn<() => Promise<PlanResponse[]>>();
const checkoutInfo = vi.fn<() => Promise<CheckoutInfo>>();
const createOrder = vi.fn<(planId: number) => Promise<OrderCreateResponse>>();
const push = vi.fn();

vi.mock("../api", () => ({ consoleApi: () => ({ listPlans, checkoutInfo, createOrder }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));
vi.mock("vue-router", () => ({ useRouter: () => ({ push }) }));

const claude: PlanResponse = {
  id: 1,
  name: "Claude 月付",
  agentType: "CLAUDE",
  durationDays: 30,
  price: 99.99,
  currency: "USD",
};
const codex: PlanResponse = {
  id: 2,
  name: "Codex 月付",
  agentType: "CODEX",
  durationDays: 30,
  price: 49,
  currency: "USD",
};

describe("PlansView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listPlans.mockResolvedValue([claude, codex]);
    checkoutInfo.mockResolvedValue({ methods: ["stripe"], stripePublishableKey: "pk" });
  });

  it("按 agent 类型分 tab，默认选第一类，卡片展示名称、时长与价格", async () => {
    const wrapper = mount(PlansView);
    await flushPromises();
    expect(wrapper.findAll(".admin-tab").map((t) => t.text().replace(/\s+/g, " "))).toEqual([
      "Claude Code 1",
      "Codex 1",
    ]);
    const card = wrapper.get(".plan-card");
    expect(card.get(".plan-name").text()).toBe("Claude 月付");
    expect(card.text()).toContain("30 天");
    expect(card.get(".plan-price").text()).toBe("99.99 USD");
  });

  it("点「购买」建单后跳支付页", async () => {
    createOrder.mockResolvedValue({ orderNo: "LN1", amountMinor: 9999, currency: "USD" });
    const wrapper = mount(PlansView);
    await flushPromises();
    await wrapper.get(".plan-card .admin-btn").trigger("click");
    await flushPromises();
    expect(createOrder).toHaveBeenCalledWith(1);
    expect(push).toHaveBeenCalledWith({ name: "PAY", params: { orderNo: "LN1" } });
  });

  it("建单失败把服务端说法原样提示，不跳转", async () => {
    createOrder.mockRejectedValue(new Error("账号当前不可购买"));
    const wrapper = mount(PlansView);
    await flushPromises();
    await wrapper.get(".plan-card .admin-btn").trigger("click");
    await flushPromises();
    expect(showToast).toHaveBeenCalledWith("error", "账号当前不可购买");
    expect(push).not.toHaveBeenCalled();
  });

  it("支付未开放时购买按钮禁用并说明", async () => {
    checkoutInfo.mockResolvedValue({ methods: [], stripePublishableKey: null });
    const wrapper = mount(PlansView);
    await flushPromises();
    expect(wrapper.get(".plan-card .admin-btn").attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("支付暂未开放");
  });

  it("没有上架套餐时是空态", async () => {
    listPlans.mockResolvedValue([]);
    const wrapper = mount(PlansView);
    await flushPromises();
    expect(wrapper.text()).toContain("暂无可购买的套餐");
  });

  it("支付状态获取失败时套餐仍可浏览，只是购买按钮禁用并提示", async () => {
    checkoutInfo.mockRejectedValue(new Error("网络错误"));
    const wrapper = mount(PlansView);
    await flushPromises();
    expect(wrapper.get(".plan-card .plan-name").text()).toBe("Claude 月付");
    expect(wrapper.get(".plan-card .admin-btn").attributes("disabled")).toBeDefined();
    expect(showToast).toHaveBeenCalledWith("error", "网络错误");
  });
});
