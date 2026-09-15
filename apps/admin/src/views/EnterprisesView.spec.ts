import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { EnterpriseResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import EnterpriseFormModal from "../components/EnterpriseFormModal.vue";
import { showToast } from "../toast";
import EnterprisesView from "./EnterprisesView.vue";

const listEnterprises = vi.fn<() => Promise<EnterpriseResponse[]>>();
const deleteEnterprise = vi.fn<(id: number) => Promise<void>>(async () => undefined);

vi.mock("../api", () => ({ adminApi: () => ({ listEnterprises, deleteEnterprise }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function enterprise(overrides: Partial<EnterpriseResponse> = {}): EnterpriseResponse {
  return {
    id: 1,
    name: "Acme 科技",
    domain: "acme.com",
    agentTypes: ["CLAUDE"],
    enabled: true,
    remark: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-02T03:04:05Z",
    ...overrides,
  };
}

const ROWS = [
  enterprise({ id: 1, name: "只用 Claude", agentTypes: ["CLAUDE"], enabled: true }),
  enterprise({ id: 2, name: "两个都用", agentTypes: ["CLAUDE", "CODEX"], enabled: true }),
  enterprise({ id: 3, name: "停用中", agentTypes: ["CODEX"], enabled: false }),
];

beforeEach(() => {
  vi.clearAllMocks();
  listEnterprises.mockResolvedValue(ROWS);
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(EnterprisesView, { attachTo: document.body });
  await flushPromises();
  return wrapper;
}

function names(wrapper: Awaited<ReturnType<typeof render>>): string[] {
  return wrapper.findAll("tbody tr").map((tr) => tr.findAll("td")[0].text());
}

describe("EnterprisesView 加载", () => {
  it("进页拉一次列表", async () => {
    const wrapper = await render();

    expect(listEnterprises).toHaveBeenCalledOnce();
    expect(names(wrapper)).toEqual(["只用 Claude", "两个都用", "停用中"]);
  });

  it("页头给总数与启用数", async () => {
    const wrapper = await render();

    expect(wrapper.get(".page-facts").text()).toContain("共 3 家");
    expect(wrapper.get(".page-facts").text()).toContain("启用 2 家");
  });

  it("拉取失败时把原因交给数据卡", async () => {
    listEnterprises.mockRejectedValueOnce(new Error("网络断了"));
    const wrapper = await render();

    expect(wrapper.findComponent(DataCard).props("error")).toBe("网络断了");
  });
});

describe("EnterprisesView 按 Agent 分", () => {
  it("一家支持两个 Agent 的企业会同时出现在两个 tab 下——tab 问的是「支持它的有哪些」", async () => {
    const wrapper = await render();
    const tabs = wrapper.findComponent({ name: "ViewTabs" });

    tabs.vm.$emit("update:modelValue", "CLAUDE");
    await wrapper.vm.$nextTick();
    expect(names(wrapper)).toEqual(["只用 Claude", "两个都用"]);

    tabs.vm.$emit("update:modelValue", "CODEX");
    await wrapper.vm.$nextTick();
    expect(names(wrapper)).toEqual(["两个都用", "停用中"]);
  });

  it("因此各 tab 计数之和可以大于总数，这不是 bug", async () => {
    const wrapper = await render();

    const options = wrapper.findComponent({ name: "ViewTabs" }).props("options") as {
      value: string;
      label: string;
      count: number;
    }[];
    expect(options).toEqual([
      { value: "ALL", label: "全部", count: 3 },
      { value: "CLAUDE", label: "Claude Code", count: 2 },
      { value: "CODEX", label: "Codex", count: 2 },
    ]);
    const perAgent = options.slice(1).reduce((sum, o) => sum + o.count, 0);
    expect(perAgent).toBeGreaterThan(3);
  });

  it("计数同样先过状态下拉，口径是「选它之后表格里有几行」", async () => {
    const wrapper = await render();

    wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", "DISABLED");
    await wrapper.vm.$nextTick();

    expect(wrapper.findComponent({ name: "ViewTabs" }).props("options")).toEqual([
      { value: "ALL", label: "全部", count: 1 },
      { value: "CLAUDE", label: "Claude Code", count: 0 },
      { value: "CODEX", label: "Codex", count: 1 },
    ]);
  });
});

describe("EnterprisesView 空态", () => {
  it("一家都没有时讲清企业是干什么用的，并直接给「新建」", async () => {
    listEnterprises.mockResolvedValue([]);
    const wrapper = await render();

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toContain("还没有企业");
    expect(wrapper.find(".admin-btn-ghost").exists()).toBe(false);
  });

  it("筛没了时给「查看全部」，一点就把两级条件都清掉", async () => {
    const wrapper = await render();
    wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", "DISABLED");
    wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", "CLAUDE");
    await wrapper.vm.$nextTick();
    expect(wrapper.findComponent(DataCard).props("empty")).toBe(true);

    await wrapper.get(".admin-btn-ghost").trigger("click");

    expect(names(wrapper)).toHaveLength(3);
  });
});

describe("EnterprisesView 增删改", () => {
  it("新建不带待编辑记录，编辑带上这一行", async () => {
    const wrapper = await render();

    await wrapper.get(".page-head-actions .admin-btn").trigger("click");
    expect(wrapper.findComponent(EnterpriseFormModal).props("editing")).toBeNull();

    await wrapper.findComponent(EnterpriseFormModal).vm.$emit("close");
    await wrapper.vm.$nextTick();
    await wrapper.findAll("tbody tr")[1].get(".admin-link").trigger("click");
    expect(wrapper.findComponent(EnterpriseFormModal).props("editing")).toMatchObject({ id: 2 });
  });

  it("存好后重新拉列表", async () => {
    const wrapper = await render();
    await wrapper.get(".page-head-actions .admin-btn").trigger("click");

    wrapper.findComponent(EnterpriseFormModal).vm.$emit("saved");
    await flushPromises();

    expect(listEnterprises).toHaveBeenCalledTimes(2);
  });

  it("删除前点名要删哪家，并提示还有「停用」这条更轻的路", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll(".admin-link")[1].trigger("click");

    const message = String(wrapper.findComponent(ConfirmDialog).props("message"));
    expect(message).toContain("只用 Claude");
    expect(message).toContain("停用");
  });

  it("确认后真删并刷新列表", async () => {
    const wrapper = await render();
    await wrapper.findAll("tbody tr")[0].findAll(".admin-link")[1].trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(deleteEnterprise).toHaveBeenCalledWith(1);
    expect(listEnterprises).toHaveBeenCalledTimes(2);
  });

  it("仍被订阅引用而删不掉时，用服务端那句中文说清为什么", async () => {
    deleteEnterprise.mockRejectedValueOnce(new BizError(410025, "企业仍被订阅引用，不能删除"));
    const wrapper = await render();
    await wrapper.findAll("tbody tr")[0].findAll(".admin-link")[1].trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "企业仍被订阅引用，不能删除");
  });
});

describe("EnterprisesView 表格内容", () => {
  it("支持多个 Agent 时逐个列成标签", async () => {
    const wrapper = await render();

    const pills = wrapper.findAll("tbody tr")[1].findAll(".pill");
    expect(pills.map((p) => p.text())).toEqual(["Claude Code", "Codex"]);
  });
});
