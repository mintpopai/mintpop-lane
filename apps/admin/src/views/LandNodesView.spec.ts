import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AdminNodeResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
import NodeProbeModal from "../components/NodeProbeModal.vue";
import { showToast } from "../toast";
import LandNodesView from "./LandNodesView.vue";

const listNodes = vi.fn<() => Promise<AdminNodeResponse[]>>();
const deleteNode = vi.fn<(id: number) => Promise<void>>(async () => undefined);

vi.mock("../api", () => ({ adminApi: () => ({ listNodes, deleteNode }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function node(overrides: Partial<AdminNodeResponse> = {}): AdminNodeResponse {
  return {
    id: 4,
    name: "LAND-东京",
    role: "LAND",
    protocol: "SOCKS5",
    serverAddr: "land.example.com",
    port: 1080,
    extraConfig: {},
    egressIp: "203.0.113.7",
    egressTimezone: "Asia/Tokyo",
    status: "ENABLED",
    remark: "",
    secretConfigured: true,
    capacity: 10,
    assignedUserCount: 3,
    airportSubscriptionId: null,
    airportSubscriptionName: null,
    sourceType: null,
    failureDomain: null,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-02T03:04:05Z",
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  // 接口是全量节点：夹具里混一个机场订阅节点，验证本页只收落地
  listNodes.mockResolvedValue([
    node(),
    node({ id: 1, name: "FRONT-A1", role: "FRONT", airportSubscriptionId: 100 }),
  ]);
  // jsdom 不实现 scrollIntoView，AdminSelect 展开面板定位高亮项时会调它
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(LandNodesView, { attachTo: document.body });
  await flushPromises();
  return wrapper;
}

type Wrapper = Awaited<ReturnType<typeof render>>;

function names(wrapper: Wrapper): string[] {
  return wrapper.findAll("tbody tr").map((tr) => tr.findAll("td")[0].text());
}

async function setStatus(wrapper: Wrapper, status: "ALL" | "ENABLED" | "DISABLED") {
  wrapper.findComponent({ name: "AdminSelect" }).vm.$emit("update:modelValue", status);
  await wrapper.vm.$nextTick();
}

describe("LandNodesView 列表", () => {
  it("只列落地节点，不混入机场订阅节点", async () => {
    const wrapper = await render();

    expect(names(wrapper)).toEqual(["LAND-东京"]);
    expect(wrapper.get(".page-title").text()).toBe("落地节点");
  });

  it("有出口 IP、出口时区、已绑 / 容量三列，没有订阅列", async () => {
    const wrapper = await render();
    const headers = wrapper.findAll("thead th").map((th) => th.text());

    expect(headers).toEqual(
      expect.arrayContaining(["出口 IP", "出口时区", "已绑 / 容量", "状态"]),
    );
    expect(headers).not.toContain("订阅");
  });

  it("已绑人数与容量一起显示；没人绑时也写出容量", async () => {
    listNodes.mockResolvedValue([node(), node({ id: 5, name: "LAND-空", assignedUserCount: 0 })]);
    const wrapper = await render();
    const cells = wrapper.findAll("tbody td").map((td) => td.text());

    expect(cells).toContain("3 / 10");
    expect(cells).toContain("0 / 10");
  });

  it("故障域列固定为「—」：落地节点没有这个概念", async () => {
    const wrapper = await render();
    const headers = wrapper.findAll("thead th").map((th) => th.text());

    expect(wrapper.findAll("tbody tr")[0].findAll("td")[headers.indexOf("故障域")].text()).toBe(
      "—",
    );
  });

  it("拉取失败时把原因交给数据卡", async () => {
    listNodes.mockRejectedValue(new BizError(110001, "服务开小差"));
    const wrapper = await render();

    expect(wrapper.findComponent(DataCard).props("error")).toBe("服务开小差");
  });
});

describe("LandNodesView 筛选与空态", () => {
  it("状态筛选：筛「停用」后只剩停用的", async () => {
    listNodes.mockResolvedValue([node(), node({ id: 5, name: "LAND-停", status: "DISABLED" })]);
    const wrapper = await render();

    await setStatus(wrapper, "DISABLED");

    expect(names(wrapper)).toEqual(["LAND-停"]);
  });

  it("一个落地节点都没有：说清出口 IP 与容量，只给「新建节点」", async () => {
    listNodes.mockResolvedValue([]);
    const wrapper = await render();

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toContain("出口 IP");
    expect(wrapper.findAll(".card-state-actions button").map((b) => b.text())).toEqual([
      "新建节点",
    ]);
  });

  it("有节点但筛没了：说「这一批里没有节点。」，给「查看全部」清条件", async () => {
    const wrapper = await render();
    await setStatus(wrapper, "DISABLED");

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toBe("这一批里没有节点。");
    const reset = wrapper.findAll(".admin-btn-ghost").find((b) => b.text() === "查看全部")!;
    await reset.trigger("click");

    expect(names(wrapper)).toEqual(["LAND-东京"]);
  });
});

describe("LandNodesView 增删改", () => {
  it("行操作是检测、编辑、删除", async () => {
    const wrapper = await render();

    expect(
      wrapper
        .findAll("tbody tr")[0]
        .findAll(".actions button")
        .map((b) => b.text()),
    ).toEqual(["检测", "编辑", "删除"]);
  });

  it("新建不带待编辑记录，编辑带上这一行；role 固定为 LAND", async () => {
    const wrapper = await render();

    await wrapper
      .findAll(".page-head-actions button")
      .find((b) => b.text() === "新建节点")!
      .trigger("click");
    expect(wrapper.findComponent(NodeFormModal).props()).toMatchObject({
      role: "LAND",
      editing: null,
    });

    wrapper.findComponent(NodeFormModal).vm.$emit("close");
    await wrapper.vm.$nextTick();
    await wrapper.findAll("tbody tr")[0].findAll(".actions button")[1].trigger("click");
    expect(wrapper.findComponent(NodeFormModal).props("editing")).toMatchObject({ id: 4 });
  });

  it("检测弹窗带着要检测的那个节点", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll(".actions button")[0].trigger("click");

    expect(wrapper.findComponent(NodeProbeModal).props("node")).toMatchObject({ id: 4 });
  });

  it("删除前点名要删哪个；仍被用户引用时用服务端那句中文", async () => {
    deleteNode.mockRejectedValueOnce(new BizError(410003, "节点仍被用户引用"));
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll(".actions button")[2].trigger("click");
    expect(String(wrapper.findComponent(ConfirmDialog).props("message"))).toContain("LAND-东京");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(deleteNode).toHaveBeenCalledWith(4);
    expect(showToast).toHaveBeenCalledWith("error", "节点仍被用户引用");
  });
});
