import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AdminNodeResponse, NodeGroupResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
import NodeProbeModal from "../components/NodeProbeModal.vue";
import SubImportModal from "../components/SubImportModal.vue";
import { showToast } from "../toast";
import NodesView from "./NodesView.vue";

const listNodes = vi.fn<() => Promise<AdminNodeResponse[]>>();
const listNodeGroups = vi.fn<() => Promise<NodeGroupResponse[]>>();
const deleteNode = vi.fn<(id: number) => Promise<void>>(async () => undefined);
const deleteNodeGroup = vi.fn<(id: number) => Promise<void>>(async () => undefined);
const renameNodeGroup = vi.fn<(id: number, body: unknown) => Promise<void>>(async () => undefined);

vi.mock("../api", () => ({
  adminApi: () => ({ listNodes, listNodeGroups, deleteNode, deleteNodeGroup, renameNodeGroup }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function node(overrides: Partial<AdminNodeResponse> = {}): AdminNodeResponse {
  return {
    id: 1,
    name: "FRONT-01",
    role: "FRONT",
    protocol: "SOCKS5",
    serverAddr: "front.example.com",
    port: 1080,
    extraConfig: {},
    egressIp: null,
    egressTimezone: null,
    status: "ENABLED",
    remark: "",
    secretConfigured: true,
    capacity: 0,
    assignedUserCount: 0,
    groupId: null,
    groupName: null,
    sourceType: null,
    failureDomain: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-02T03:04:05Z",
    ...overrides,
  };
}

function group(overrides: Partial<NodeGroupResponse> = {}): NodeGroupResponse {
  return {
    id: 100,
    name: "机场 A",
    subUrlMasked: "https://sub.example.com/***",
    // 服务端给的是全量计数，页面刻意不用它
    nodeCount: 999,
    remark: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
    ...overrides,
  };
}

const GROUPS = [group({ id: 100, name: "机场 A" }), group({ id: 200, name: "机场 B" })];
const NODES = [
  node({ id: 1, name: "FRONT-A1", role: "FRONT", groupId: 100, groupName: "机场 A" }),
  node({
    id: 2,
    name: "FRONT-A2",
    role: "FRONT",
    groupId: 100,
    groupName: "机场 A",
    status: "DISABLED",
  }),
  node({ id: 3, name: "FRONT-散", role: "FRONT", groupId: null, groupName: null }),
  node({
    id: 4,
    name: "LAND-东京",
    role: "LAND",
    egressIp: "203.0.113.7",
    egressTimezone: "Asia/Tokyo",
    capacity: 10,
    assignedUserCount: 3,
  }),
];

beforeEach(() => {
  vi.clearAllMocks();
  listNodes.mockResolvedValue(NODES);
  listNodeGroups.mockResolvedValue(GROUPS);
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(NodesView, { attachTo: document.body });
  await flushPromises();
  return wrapper;
}

type Wrapper = Awaited<ReturnType<typeof render>>;

function names(wrapper: Wrapper): string[] {
  return wrapper.findAll("tbody tr").map((tr) => tr.findAll("td")[0].text());
}

async function switchTo(wrapper: Wrapper, role: "FRONT" | "LAND") {
  wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", role);
  await wrapper.vm.$nextTick();
}

async function setStatus(wrapper: Wrapper, status: "ALL" | "ENABLED" | "DISABLED") {
  wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", status);
  await wrapper.vm.$nextTick();
}

async function setGroup(wrapper: Wrapper, value: "ALL" | "NONE" | number) {
  wrapper.findComponent({ name: "FilterChips" }).vm.$emit("update:modelValue", value);
  await wrapper.vm.$nextTick();
}

describe("NodesView 加载", () => {
  it("进页同时拉节点与分组，默认停在第一跳", async () => {
    const wrapper = await render();

    expect(listNodes).toHaveBeenCalledOnce();
    expect(listNodeGroups).toHaveBeenCalledOnce();
    expect(names(wrapper)).toEqual(["FRONT-A1", "FRONT-A2", "FRONT-散"]);
  });

  it("页头给整页规模：节点总数与分组数，不随筛选变", async () => {
    const wrapper = await render();

    expect(wrapper.get(".page-facts").text()).toContain("共 4 个节点");
    expect(wrapper.get(".page-facts").text()).toContain("2");

    await setStatus(wrapper, "DISABLED");
    expect(wrapper.get(".page-facts").text()).toContain("共 4 个节点");
  });

  it("拉取失败时把原因交给数据卡", async () => {
    listNodes.mockRejectedValueOnce(new BizError(410002, "没权限看节点"));
    const wrapper = await render();

    expect(wrapper.findComponent(DataCard).props("error")).toBe("没权限看节点");
  });
});

describe("NodesView 按跳数分", () => {
  it("只有两跳、没有「全部」一档：两跳的表格列不同，合不到一起看", async () => {
    const wrapper = await render();

    expect(wrapper.findComponent({ name: "ViewTabs" }).props("options")).toEqual([
      { value: "FRONT", label: "第一跳（出国）", count: 3 },
      { value: "LAND", label: "第二跳（落地）", count: 1 },
    ]);
  });

  it("tab 计数先过状态下拉，口径是「选它之后表格里有几行」", async () => {
    const wrapper = await render();

    await setStatus(wrapper, "DISABLED");

    expect(wrapper.findComponent({ name: "ViewTabs" }).props("options")).toEqual([
      { value: "FRONT", label: "第一跳（出国）", count: 1 },
      { value: "LAND", label: "第二跳（落地）", count: 0 },
    ]);
  });

  it("落地节点多出口 IP、时区、容量三列，第一跳没有", async () => {
    const wrapper = await render();
    const frontHeaders = wrapper.findAll("thead th").map((th) => th.text());
    expect(frontHeaders).not.toContain("出口 IP");
    expect(frontHeaders).toContain("分组");

    await switchTo(wrapper, "LAND");
    const landHeaders = wrapper.findAll("thead th").map((th) => th.text());
    expect(landHeaders).toContain("出口 IP");
    expect(landHeaders).toContain("出口时区");
    expect(landHeaders).toContain("已绑 / 容量");
    // 分组只属于第一跳
    expect(landHeaders).not.toContain("分组");
  });

  it("检测只对落地节点开放：前置节点走加密协议，服务端没内核连不了", async () => {
    const wrapper = await render();
    expect(
      wrapper
        .findAll("tbody tr")[0]
        .findAll(".actions button")
        .map((b) => b.text()),
    ).toEqual(["编辑", "删除"]);

    await switchTo(wrapper, "LAND");
    expect(
      wrapper
        .findAll("tbody tr")[0]
        .findAll(".actions button")
        .map((b) => b.text()),
    ).toEqual(["检测", "编辑", "删除"]);
  });
});

describe("NodesView 分组带", () => {
  it("分组是第一跳的主视角，落地那边整条 chip 带都不出", async () => {
    const wrapper = await render();
    expect(wrapper.findComponent({ name: "FilterChips" }).exists()).toBe(true);

    await switchTo(wrapper, "LAND");
    expect(wrapper.findComponent({ name: "FilterChips" }).exists()).toBe(false);
  });

  it("分组计数按本地口径数，不用服务端那个全量的 nodeCount", async () => {
    const wrapper = await render();

    expect(wrapper.findComponent({ name: "FilterChips" }).props("options")).toEqual([
      { value: "ALL", label: "全部", count: 3 },
      { value: "NONE", label: "未分组", count: 1 },
      { value: 100, label: "机场 A", count: 2 },
      { value: 200, label: "机场 B", count: 0 },
    ]);
    // 服务端说 999，页面不能跟着说 999
    expect(GROUPS[0].nodeCount).toBe(999);
  });

  it("选某个分组就只剩那一批，选「未分组」只剩散的", async () => {
    const wrapper = await render();

    await setGroup(wrapper, 100);
    expect(names(wrapper)).toEqual(["FRONT-A1", "FRONT-A2"]);

    await setGroup(wrapper, "NONE");
    expect(names(wrapper)).toEqual(["FRONT-散"]);
  });

  it("分组与状态两级条件叠加", async () => {
    const wrapper = await render();

    await setGroup(wrapper, 100);
    await setStatus(wrapper, "ENABLED");

    expect(names(wrapper)).toEqual(["FRONT-A1"]);
  });

  it("选中分组才露出它的操作，没选时工具条里没有这几个口子", async () => {
    const wrapper = await render();
    const groupActions = () => wrapper.findAll(".admin-toolbar .admin-link").map((b) => b.text());
    expect(groupActions()).toEqual([]);

    await setGroup(wrapper, 100);
    expect(groupActions()).toEqual(["重新拉取", "改名", "删除分组"]);
  });

  it("切到落地 tab 后，上一次选中的分组操作不会漏进来", async () => {
    const wrapper = await render();
    await setGroup(wrapper, 100);
    expect(wrapper.findAll(".admin-toolbar .admin-link")).toHaveLength(3);

    await switchTo(wrapper, "LAND");

    expect(wrapper.findAll(".admin-toolbar .admin-link")).toHaveLength(0);
  });

  it("选中的分组被删掉后，筛选回落到「全部」而不是卡在一个不存在的组上", async () => {
    const wrapper = await render();
    await setGroup(wrapper, 100);
    expect(names(wrapper)).toHaveLength(2);

    listNodeGroups.mockResolvedValue([group({ id: 200, name: "机场 B" })]);
    listNodes.mockResolvedValue(NODES.filter((n) => n.groupId !== 100));
    wrapper.findAll(".admin-toolbar .admin-link")[2].trigger("click");
    await wrapper.vm.$nextTick();
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(names(wrapper)).toEqual(["FRONT-散"]);
  });
});

describe("NodesView 分组操作", () => {
  async function selectGroupAnd(wrapper: Wrapper, action: "重新拉取" | "改名" | "删除分组") {
    await setGroup(wrapper, 100);
    const button = wrapper.findAll(".admin-toolbar .admin-link").find((b) => b.text() === action)!;
    await button.trigger("click");
  }

  it("改名时先把原名填进输入框，省得重打", async () => {
    const wrapper = await render();

    await selectGroupAnd(wrapper, "改名");

    expect(document.querySelector<HTMLInputElement>(".dialog .admin-input")?.value).toBe("机场 A");
  });

  it("改名不许改成空的", async () => {
    const wrapper = await render();
    await selectGroupAnd(wrapper, "改名");

    const input = document.querySelector<HTMLInputElement>(".dialog .admin-input")!;
    input.value = "   ";
    input.dispatchEvent(new Event("input"));
    await wrapper.vm.$nextTick();
    document.querySelector<HTMLButtonElement>(".dialog .admin-btn")!.click();
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "分组名不能为空");
    expect(renameNodeGroup).not.toHaveBeenCalled();
  });

  it("改名成功后刷新列表", async () => {
    const wrapper = await render();
    await selectGroupAnd(wrapper, "改名");

    document.querySelector<HTMLButtonElement>(".dialog .admin-btn")!.click();
    await flushPromises();

    expect(renameNodeGroup).toHaveBeenCalledWith(100, { name: "机场 A", remark: "" });
    expect(showToast).toHaveBeenCalledWith("success", "已改名");
    expect(listNodes).toHaveBeenCalledTimes(2);
  });

  it("删除分组会连带组内节点，确认文案要说清这一点", async () => {
    const wrapper = await render();

    await selectGroupAnd(wrapper, "删除分组");

    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(true);
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(deleteNodeGroup).toHaveBeenCalledWith(100);
    expect(showToast).toHaveBeenCalledWith("success", "已删除分组及其节点");
  });

  it("组内有节点被用户绑着时删不掉，用服务端那句中文", async () => {
    deleteNodeGroup.mockRejectedValueOnce(new BizError(410013, "组内节点仍被用户绑定"));
    const wrapper = await render();
    await selectGroupAnd(wrapper, "删除分组");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "组内节点仍被用户绑定");
  });

  it("「重新拉取」打开的是订阅导入弹窗，带着这个分组", async () => {
    const wrapper = await render();

    await selectGroupAnd(wrapper, "重新拉取");

    expect(wrapper.findComponent(SubImportModal).exists()).toBe(true);
  });
});

describe("NodesView 空态", () => {
  it("这一跳一个节点都没有时，说清这一跳是干什么的", async () => {
    listNodes.mockResolvedValue([node({ id: 4, role: "LAND" })]);
    const wrapper = await render();

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toContain(
      "还没有第一跳节点",
    );
  });

  it("落地那一跳的空态说的是出口 IP 与容量", async () => {
    listNodes.mockResolvedValue([node({ id: 1, role: "FRONT" })]);
    const wrapper = await render();
    await switchTo(wrapper, "LAND");

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toContain("出口 IP");
  });

  it("有节点但筛没了是另一回事：空态说法按原始数量判，不按筛完的", async () => {
    // 三个第一跳节点里没有「未分组 + 已禁用」的组合
    const wrapper = await render();

    await setGroup(wrapper, "NONE");
    await setStatus(wrapper, "DISABLED");

    expect(wrapper.findComponent(DataCard).props("empty")).toBe(true);
    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toBe("这一批里没有节点。");
  });

  it("筛没了时给「查看全部」，一点把两级条件都清掉", async () => {
    const wrapper = await render();
    await setGroup(wrapper, "NONE");
    await setStatus(wrapper, "DISABLED");

    const reset = wrapper.findAll(".admin-btn-ghost").find((b) => b.text() === "查看全部")!;
    await reset.trigger("click");

    expect(names(wrapper)).toHaveLength(3);
  });

  it("空态给的路与页头右上那对按钮一致：第一跳两条路，落地只有一条", async () => {
    listNodes.mockResolvedValue([]);
    const wrapper = await render();

    const frontActions = wrapper.findAll(".card-state-actions button").map((b) => b.text());
    expect(frontActions).toEqual(["从订阅导入", "新建节点"]);

    await switchTo(wrapper, "LAND");
    // 落地节点没有订阅导入这条路
    expect(wrapper.findAll(".card-state-actions button").map((b) => b.text())).toEqual([
      "新建节点",
    ]);
  });
});

describe("NodesView 增删改", () => {
  it("「从订阅导入」只在第一跳出现在页头", async () => {
    const wrapper = await render();
    expect(wrapper.findAll(".page-head-actions button").map((b) => b.text())).toEqual([
      "从订阅导入",
      "新建节点",
    ]);

    await switchTo(wrapper, "LAND");
    expect(wrapper.findAll(".page-head-actions button").map((b) => b.text())).toEqual(["新建节点"]);
  });

  it("新建不带待编辑记录，编辑带上这一行", async () => {
    const wrapper = await render();

    await wrapper.findAll(".page-head-actions button")[1].trigger("click");
    expect(wrapper.findComponent(NodeFormModal).props("editing")).toBeNull();

    wrapper.findComponent(NodeFormModal).vm.$emit("close");
    await wrapper.vm.$nextTick();
    await wrapper.findAll("tbody tr")[0].findAll(".actions button")[0].trigger("click");
    expect(wrapper.findComponent(NodeFormModal).props("editing")).toMatchObject({
      id: 1,
      name: "FRONT-A1",
    });
  });

  it("检测弹窗带着要检测的那个落地节点", async () => {
    const wrapper = await render();
    await switchTo(wrapper, "LAND");

    await wrapper.findAll("tbody tr")[0].findAll(".actions button")[0].trigger("click");

    expect(wrapper.findComponent(NodeProbeModal).props("node")).toMatchObject({ id: 4 });
  });

  it("删除节点前点名要删的是哪个", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll(".actions button")[1].trigger("click");

    expect(String(wrapper.findComponent(ConfirmDialog).props("message"))).toContain("FRONT-A1");
  });

  it("节点仍被用户引用而删不掉时，用服务端那句中文", async () => {
    deleteNode.mockRejectedValueOnce(new BizError(410003, "节点仍被用户引用"));
    const wrapper = await render();
    await wrapper.findAll("tbody tr")[0].findAll(".actions button")[1].trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "节点仍被用户引用");
  });
});

describe("NodesView 表格内容", () => {
  it("落地节点把已绑人数与容量一起显示，一眼看出还能分几个", async () => {
    const wrapper = await render();
    await switchTo(wrapper, "LAND");

    expect(
      wrapper
        .findAll("tbody tr")[0]
        .findAll("td")
        .map((td) => td.text()),
    ).toContain("3 / 10");
  });

  it("没人绑时也把容量说出来，不是留空", async () => {
    listNodes.mockResolvedValue([
      node({ id: 4, role: "LAND", capacity: 10, assignedUserCount: 0 }),
    ]);
    const wrapper = await render();
    await switchTo(wrapper, "LAND");

    expect(
      wrapper
        .findAll("tbody tr")[0]
        .findAll("td")
        .map((td) => td.text()),
    ).toContain("0 / 10");
  });

  it("有 sourceType（订阅导入的）时显示它，否则退回协议名", async () => {
    listNodes.mockResolvedValue([
      node({ id: 1, sourceType: "vmess" }),
      node({ id: 2, name: "手工建的", sourceType: null, protocol: "SOCKS5" }),
    ]);
    const wrapper = await render();

    const rows = wrapper.findAll("tbody tr");
    expect(rows[0].findAll("td")[1].text()).toBe("vmess");
    expect(rows[1].findAll("td")[1].text()).toBe("SOCKS5");
  });

  it("故障域：有值就展示域名，null 展示「未解析」而不是留空", async () => {
    listNodes.mockResolvedValue([
      node({ id: 1, name: "US-01", role: "FRONT", failureDomain: "jp.tsdns.top" }),
      node({ id: 2, name: "US-02", role: "FRONT", failureDomain: null }),
    ]);
    const wrapper = await render();

    expect(wrapper.text()).toContain("jp.tsdns.top");
    expect(wrapper.text()).toContain("未解析");
  });

  it("落地节点没有故障域这个概念，故障域列固定展示占位符「—」", async () => {
    listNodes.mockResolvedValue([node({ id: 4, role: "LAND", failureDomain: null })]);
    const wrapper = await render();
    await switchTo(wrapper, "LAND");

    // 落地列序：节点名/协议/地址/故障域/出口 IP/…（落地没有分组列），故障域是第 4 格
    const failureDomainCell = wrapper.findAll("tbody tr")[0].findAll("td")[3];
    expect(failureDomainCell.text()).toBe("—");
  });
});
