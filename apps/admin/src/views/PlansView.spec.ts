import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { PlanResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PlanFormModal from "../components/PlanFormModal.vue";
import { showToast } from "../toast";
import TruncatedText from "../components/TruncatedText.vue";
import PlansView from "./PlansView.vue";

const listPlans = vi.fn<() => Promise<PlanResponse[]>>();
const deletePlan = vi.fn<(id: number) => Promise<void>>(async () => undefined);

vi.mock("../api", () => ({ adminApi: () => ({ listPlans, deletePlan }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function plan(overrides: Partial<PlanResponse> = {}): PlanResponse {
  return {
    id: 1,
    name: "月付套餐",
    agentType: "CLAUDE",
    durationDays: 30,
    price: 29.9,
    currency: "USD",
    description: null,
    imageUrl: null,
    enabled: true,
    remark: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-02T03:04:05Z",
    ...overrides,
  };
}

const ROWS = [
  plan({ id: 1, name: "Claude 月付", agentType: "CLAUDE", enabled: true }),
  plan({ id: 2, name: "Claude 年付", agentType: "CLAUDE", enabled: false }),
  plan({ id: 3, name: "Codex 月付", agentType: "CODEX", enabled: true }),
];

beforeEach(() => {
  vi.clearAllMocks();
  listPlans.mockResolvedValue(ROWS);
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(PlansView, { attachTo: document.body });
  await flushPromises();
  return wrapper;
}

function names(wrapper: Awaited<ReturnType<typeof render>>): string[] {
  return wrapper.findAll("tbody tr").map((tr) => tr.findAll("td")[0].text());
}

describe("PlansView 加载", () => {
  it("进页就拉一次列表，把套餐铺进表格", async () => {
    const wrapper = await render();

    expect(listPlans).toHaveBeenCalledOnce();
    expect(names(wrapper)).toEqual(["Claude 月付", "Claude 年付", "Codex 月付"]);
  });

  it("页头的总数与上架数是整页规模，不随筛选变", async () => {
    const wrapper = await render();
    const facts = () => wrapper.get(".page-facts").text();

    expect(facts()).toContain("共 3 个");
    expect(facts()).toContain("上架 2 个");

    wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", "CODEX");
    await wrapper.vm.$nextTick();

    expect(names(wrapper)).toEqual(["Codex 月付"]);
    // 表格只剩一行，但页头说的仍是整页规模
    expect(facts()).toContain("共 3 个");
  });

  it("拉取失败时把服务端那句中文交给数据卡，不是空表格", async () => {
    listPlans.mockRejectedValueOnce(new BizError(410001, "没权限看套餐"));
    const wrapper = await render();

    expect(wrapper.findComponent(DataCard).props("error")).toBe("没权限看套餐");
  });
});

describe("PlansView 两级筛选", () => {
  it("一级按 Agent 分，选中哪个就只剩哪个的套餐", async () => {
    const wrapper = await render();

    wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", "CLAUDE");
    await wrapper.vm.$nextTick();

    expect(names(wrapper)).toEqual(["Claude 月付", "Claude 年付"]);
  });

  it("tab 计数的口径是「选它之后表格里会有多少行」，所以先过状态下拉", async () => {
    const wrapper = await render();
    const tabs = wrapper.findComponent({ name: "ViewTabs" });

    expect(tabs.props("options")).toEqual([
      { value: "ALL", label: "全部", count: 3 },
      { value: "CLAUDE", label: "Claude Code", count: 2 },
      { value: "CODEX", label: "Codex", count: 1 },
    ]);

    // 状态收窄到「上架」后，各 tab 的计数要跟着缩
    wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", "ENABLED");
    await wrapper.vm.$nextTick();

    expect(tabs.props("options")).toEqual([
      { value: "ALL", label: "全部", count: 2 },
      { value: "CLAUDE", label: "Claude Code", count: 1 },
      { value: "CODEX", label: "Codex", count: 1 },
    ]);
  });

  it("两级条件叠加生效", async () => {
    const wrapper = await render();

    wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", "CLAUDE");
    wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", "DISABLED");
    await wrapper.vm.$nextTick();

    expect(names(wrapper)).toEqual(["Claude 年付"]);
  });
});

describe("PlansView 空态", () => {
  it("一个套餐都没有时，讲清套餐是什么、并直接给「新建」", async () => {
    listPlans.mockResolvedValue([]);
    const wrapper = await render();

    const card = wrapper.findComponent(DataCard);
    expect(card.props("empty")).toBe(true);
    expect(String(card.props("emptyText"))).toContain("还没有套餐");
    expect(wrapper.find(".admin-btn-ghost").exists()).toBe(false);
  });

  it("有套餐但筛没了是另一回事，给的是「查看全部」", async () => {
    const wrapper = await render();
    wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", "DISABLED");
    wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", "CODEX");
    await wrapper.vm.$nextTick();

    expect(wrapper.findComponent(DataCard).props("empty")).toBe(true);
    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toBe("这一批里没有套餐。");

    await wrapper.get(".admin-btn-ghost").trigger("click");
    expect(names(wrapper)).toHaveLength(3);
  });
});

describe("PlansView 增删改", () => {
  it("新建时弹窗不带待编辑记录", async () => {
    const wrapper = await render();

    await wrapper.get(".page-head-actions .admin-btn").trigger("click");

    expect(wrapper.findComponent(PlanFormModal).props("editing")).toBeNull();
  });

  it("编辑时把这一行交给弹窗回填", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[1].get(".admin-link").trigger("click");

    expect(wrapper.findComponent(PlanFormModal).props("editing")).toMatchObject({
      id: 2,
      name: "Claude 年付",
    });
  });

  it("弹窗存好后重新拉列表，页面上立刻是新数据", async () => {
    const wrapper = await render();
    await wrapper.get(".page-head-actions .admin-btn").trigger("click");

    wrapper.findComponent(PlanFormModal).vm.$emit("saved");
    await flushPromises();

    expect(listPlans).toHaveBeenCalledTimes(2);
  });

  it("删除前先确认，弹窗里点名要删的是哪个套餐", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll(".admin-link")[1].trigger("click");

    const confirm = wrapper.findComponent(ConfirmDialog);
    expect(confirm.exists()).toBe(true);
    expect(String(confirm.props("message"))).toContain("Claude 月付");
    // 提醒还有「停用」这条更轻的路
    expect(String(confirm.props("message"))).toContain("停用");
  });

  it("确认后才真删，删完重新拉列表", async () => {
    const wrapper = await render();
    await wrapper.findAll("tbody tr")[0].findAll(".admin-link")[1].trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(deletePlan).toHaveBeenCalledWith(1);
    expect(showToast).toHaveBeenCalledWith("success", "已删除");
    expect(listPlans).toHaveBeenCalledTimes(2);
    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(false);
  });

  it("取消确认时什么也不做", async () => {
    const wrapper = await render();
    await wrapper.findAll("tbody tr")[0].findAll(".admin-link")[1].trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("cancel");
    await wrapper.vm.$nextTick();

    expect(deletePlan).not.toHaveBeenCalled();
    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(false);
  });

  it("删不掉时用服务端那句中文，确认框留着让人自己决定下一步", async () => {
    deletePlan.mockRejectedValueOnce(new BizError(410019, "套餐仍被订阅引用"));
    const wrapper = await render();
    await wrapper.findAll("tbody tr")[0].findAll(".admin-link")[1].trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "套餐仍被订阅引用");
    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(true);
  });
});

describe("PlansView 表格内容", () => {
  it("价格恒两位小数并带币种，时长带单位", async () => {
    listPlans.mockResolvedValue([plan({ price: 29.9, currency: "USD", durationDays: 30 })]);
    const wrapper = await render();

    const cells = wrapper.findAll("tbody tr td").map((td) => td.text());
    expect(cells).toContain("29.90 USD");
    expect(cells).toContain("30 天");
  });

  it("备注为空时出占位符，不留空白格", async () => {
    listPlans.mockResolvedValue([plan({ remark: null })]);
    const wrapper = await render();

    expect(wrapper.findAll("tbody tr td")[5].text()).not.toBe("");
  });

  it("备注交给 TruncatedText：长备注不把整张表撑长，原文靠悬浮看全", async () => {
    const long = "这是一条很长的备注".repeat(8);
    listPlans.mockResolvedValue([plan({ remark: long })]);
    const wrapper = await render();

    expect(wrapper.findAll("tbody tr td")[5].findComponent(TruncatedText).props("text")).toBe(long);
  });
});
