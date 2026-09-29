import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { reactive } from "vue";
import { BizError } from "../api/http";
import type { AdminNodeResponse, AirportSubscriptionResponse } from "../api/types";
import AirportSubscriptionEditModal from "../components/AirportSubscriptionEditModal.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import NodeFormModal from "../components/NodeFormModal.vue";
import SubImportModal from "../components/SubImportModal.vue";
import { showToast } from "../toast";
import SubscriptionDetailView from "./SubscriptionDetailView.vue";

const listAirportSubscriptions = vi.fn<() => Promise<AirportSubscriptionResponse[]>>();
const listNodes = vi.fn<() => Promise<AdminNodeResponse[]>>();
const deleteAirportSubscription = vi.fn<(id: number) => Promise<void>>(async () => undefined);

vi.mock("../api", () => ({
  adminApi: () => ({ listAirportSubscriptions, listNodes, deleteAirportSubscription }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

const route = reactive<{ params: Record<string, string> }>({ params: { id: "100" } });
const push = vi.fn();
vi.mock("vue-router", () => ({ useRoute: () => route, useRouter: () => ({ push }) }));

function sub(overrides: Partial<AirportSubscriptionResponse> = {}): AirportSubscriptionResponse {
  return {
    id: 100,
    name: "taishan",
    subUrlMasked: "https://sub.example.com/***",
    nodeCount: 999,
    remark: "主力订阅",
    airportId: 1,
    airportName: "泰山云",
    account: "a@x.com",
    bandwidthMbps: 300,
    usedBytes: 64,
    totalBytes: 100,
    expiresAt: "2027-05-02T00:00:00Z",
    fetchedAt: "2026-09-29T07:58:00Z",
    fetchFailedSince: null,
    lastFetchError: null,
    primaryUsed: 0,
    primaryCapacity: 15,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
    ...overrides,
  };
}

function node(overrides: Partial<AdminNodeResponse> = {}): AdminNodeResponse {
  return {
    id: 1,
    name: "US-01",
    role: "FRONT",
    protocol: "MIHOMO",
    serverAddr: "us01.example.com",
    port: 35660,
    extraConfig: {},
    egressIp: null,
    egressTimezone: null,
    status: "ENABLED",
    remark: "",
    secretConfigured: true,
    capacity: 0,
    assignedUserCount: 0,
    airportSubscriptionId: 100,
    airportSubscriptionName: "taishan",
    sourceType: "anytls",
    failureDomain: "jp.tsdns.top",
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-02T03:04:05Z",
    ...overrides,
  };
}

const NODES = [
  node({ id: 1, name: "US-01" }),
  node({ id: 2, name: "US-02", failureDomain: null }),
  node({ id: 3, name: "别家-01", airportSubscriptionId: 200, airportSubscriptionName: "xxx" }),
  node({ id: 4, name: "LAND-东京", role: "LAND", airportSubscriptionId: null }),
];

beforeEach(() => {
  vi.clearAllMocks();
  route.params = { id: "100" };
  listAirportSubscriptions.mockResolvedValue([sub(), sub({ id: 200, name: "xxx" })]);
  listNodes.mockResolvedValue(NODES);
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(SubscriptionDetailView, {
    attachTo: document.body,
    global: {
      stubs: { RouterLink: { name: "RouterLink", props: ["to"], template: "<a><slot /></a>" } },
    },
  });
  await flushPromises();
  return wrapper;
}

type Wrapper = Awaited<ReturnType<typeof render>>;

function nodeNames(wrapper: Wrapper): string[] {
  return wrapper.findAll("tbody tr").map((tr) => tr.findAll("td")[0].text());
}

function headButton(wrapper: Wrapper, text: string) {
  return wrapper.findAll(".detail-actions button").find((b) => b.text() === text)!;
}

describe("SubscriptionDetailView 订阅信息", () => {
  it("标题是订阅名；信息区给出机场、账号、打码链接、带宽、主用名额、额度、到期与备注", async () => {
    const wrapper = await render();
    const info = wrapper.get(".detail-info").text();

    expect(wrapper.get(".page-title").text()).toBe("taishan");
    expect(info).toContain("泰山云");
    expect(info).toContain("a@x.com");
    expect(info).toContain("https://sub.example.com/***");
    expect(info).toContain("300 Mbps");
    expect(info).toContain("0 / 15");
    expect(info).toContain("64%");
    expect(info).toContain("主力订阅");
  });

  it("返回链接带上所属机场，回到原来那个页签", async () => {
    const wrapper = await render();

    expect(wrapper.getComponent({ name: "RouterLink" }).props("to")).toEqual({
      name: "AIRPORT_SUBSCRIPTIONS",
      query: { airport: "1" },
    });
  });

  it("拉取失败时出红条，写明起始时间与原因；正常时没有", async () => {
    const ok = await render();
    expect(ok.find(".fetch-failed-banner").exists()).toBe(false);
    ok.unmount();

    listAirportSubscriptions.mockResolvedValue([
      sub({ fetchFailedSince: "2026-09-29T06:00:00Z", lastFetchError: "HTTP 403" }),
    ]);
    const failed = await render();
    expect(failed.get(".fetch-failed-banner").text()).toContain("HTTP 403");
  });

  it("订阅不存在（已删或 id 非法）：只给提示与返回链接，不画节点表", async () => {
    route.params = { id: "999" };
    const missing = await render();
    expect(missing.text()).toContain("订阅不存在或已被删除。");
    expect(missing.find("table").exists()).toBe(false);
    missing.unmount();

    route.params = { id: "abc" };
    const invalid = await render();
    expect(invalid.text()).toContain("订阅不存在或已被删除。");
  });

  it("接口失败时显示错误原因，而不是误报「订阅不存在」", async () => {
    listAirportSubscriptions.mockRejectedValue(new BizError(110001, "服务开小差"));
    const wrapper = await render();

    expect(wrapper.text()).toContain("服务开小差");
    expect(wrapper.text()).not.toContain("订阅不存在");
  });
});

describe("SubscriptionDetailView 节点表", () => {
  it("只列本订阅的机场订阅节点：别家订阅与落地节点都不混进来", async () => {
    const wrapper = await render();

    expect(nodeNames(wrapper)).toEqual(["US-01", "US-02"]);
    expect(wrapper.get(".nodes-title").text()).toContain("2");
  });

  it("列不含「订阅」；故障域有值展示域名，null 展示「未解析」", async () => {
    const wrapper = await render();

    expect(wrapper.findAll("thead th").map((th) => th.text())).not.toContain("订阅");
    expect(wrapper.text()).toContain("jp.tsdns.top");
    expect(wrapper.text()).toContain("未解析");
  });

  it("节点只有「编辑」，没有删除：订阅刷新会把手删的节点对齐回来", async () => {
    const wrapper = await render();

    expect(
      wrapper
        .findAll("tbody tr")[0]
        .findAll(".actions button")
        .map((b) => b.text()),
    ).toEqual(["编辑"]);
  });

  it("编辑打开节点表单，role 为 FRONT，带上这一行", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].get(".actions button").trigger("click");

    expect(wrapper.findComponent(NodeFormModal).props()).toMatchObject({
      role: "FRONT",
      editing: { id: 1 },
    });
  });

  it("订阅里一个节点都没有时，提示可以重新拉取", async () => {
    listNodes.mockResolvedValue([]);
    const wrapper = await render();

    expect(wrapper.text()).toContain("这个订阅里还没有节点");
  });
});

describe("SubscriptionDetailView 订阅操作", () => {
  it("「重新拉取」打开导入弹窗并带上这个订阅", async () => {
    const wrapper = await render();

    await headButton(wrapper, "重新拉取").trigger("click");

    expect(wrapper.findComponent(SubImportModal).props("group")).toMatchObject({ id: 100 });
  });

  it("「编辑」打开订阅编辑弹窗；saved 后重新加载", async () => {
    const wrapper = await render();

    await headButton(wrapper, "编辑").trigger("click");
    expect(wrapper.findComponent(AirportSubscriptionEditModal).props("subscription")).toMatchObject(
      { id: 100 },
    );

    wrapper.findComponent(AirportSubscriptionEditModal).vm.$emit("saved");
    await flushPromises();
    expect(listAirportSubscriptions).toHaveBeenCalledTimes(2);
    expect(listNodes).toHaveBeenCalledTimes(2);
  });

  it("删除订阅：文案说清节点会一并删除；成功后回到所属机场的页签", async () => {
    const wrapper = await render();

    await headButton(wrapper, "删除订阅").trigger("click");
    expect(String(wrapper.findComponent(ConfirmDialog).props("message"))).toContain(
      "2 个节点会一并删除",
    );

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(deleteAirportSubscription).toHaveBeenCalledWith(100);
    expect(showToast).toHaveBeenCalledWith("success", "已删除订阅及其节点");
    expect(push).toHaveBeenCalledWith({
      name: "AIRPORT_SUBSCRIPTIONS",
      query: { airport: "1" },
    });
  });

  it("订阅仍被用户引用删不掉时，用服务端那句中文，且不跳走", async () => {
    deleteAirportSubscription.mockRejectedValueOnce(new BizError(410013, "订阅仍被用户的线路引用"));
    const wrapper = await render();
    await headButton(wrapper, "删除订阅").trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "订阅仍被用户的线路引用");
    expect(push).not.toHaveBeenCalled();
  });
});
