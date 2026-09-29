import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { reactive } from "vue";
import { BizError } from "../api/http";
import type { AirportResponse, AirportSubscriptionResponse } from "../api/types";
import AirportFormModal from "../components/AirportFormModal.vue";
import AirportSubscriptionAuditModal from "../components/AirportSubscriptionAuditModal.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import SubImportModal from "../components/SubImportModal.vue";
import { showToast } from "../toast";
import AirportSubscriptionsView from "./AirportSubscriptionsView.vue";

const listAirports = vi.fn<() => Promise<AirportResponse[]>>();
const listAirportSubscriptions = vi.fn<() => Promise<AirportSubscriptionResponse[]>>();
const deleteAirport = vi.fn<(id: number) => Promise<void>>(async () => undefined);

vi.mock("../api", () => ({
  adminApi: () => ({ listAirports, listAirportSubscriptions, deleteAirport }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

// 路由桩：route 是响应式的，replace 会真的改 query，这样「切 tab ↔ URL」能双向验证
const route = reactive<{ query: Record<string, string> }>({ query: {} });
const push = vi.fn();
const replace = vi.fn(async (location: { query: Record<string, string> }) => {
  route.query = location.query;
});
vi.mock("vue-router", () => ({ useRoute: () => route, useRouter: () => ({ push, replace }) }));

function airport(overrides: Partial<AirportResponse> = {}): AirportResponse {
  return {
    id: 1,
    name: "泰山云",
    websiteUrl: "https://taishan.example.com",
    remark: "老牌机场",
    subscriptionCount: 2,
    primaryUsed: 7,
    primaryCapacity: 30,
    createdAt: "2026-09-28T00:00:00Z",
    updatedAt: "2026-09-28T00:00:00Z",
    ...overrides,
  };
}

function sub(overrides: Partial<AirportSubscriptionResponse> = {}): AirportSubscriptionResponse {
  return {
    id: 100,
    name: "taishan",
    subUrlMasked: "https://sub.example.com/***",
    nodeCount: 12,
    remark: null,
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

const AIRPORTS = [
  airport({ id: 1, name: "泰山云" }),
  airport({ id: 2, name: "某机场B", websiteUrl: null, remark: null }),
];
const SUBS = [
  sub({ id: 100, name: "taishan", airportId: 1 }),
  sub({ id: 101, name: "xxx", airportId: 1 }),
  sub({ id: 200, name: "b-main", airportId: 2, airportName: "某机场B" }),
];

beforeEach(() => {
  vi.clearAllMocks();
  route.query = {};
  listAirports.mockResolvedValue(AIRPORTS);
  listAirportSubscriptions.mockResolvedValue(SUBS);
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(AirportSubscriptionsView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: { props: ["to"], template: "<a><slot /></a>" } } },
  });
  await flushPromises();
  return wrapper;
}

type Wrapper = Awaited<ReturnType<typeof render>>;

function subNames(wrapper: Wrapper): string[] {
  return wrapper.findAll("tbody tr").map((tr) => tr.findAll("td")[0].text());
}

async function selectTab(wrapper: Wrapper, airportId: number) {
  wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", airportId);
  await flushPromises();
}

function barButton(wrapper: Wrapper, text: string) {
  return wrapper.findAll(".airport-bar button").find((b) => b.text() === text)!;
}

describe("AirportSubscriptionsView 机场页签", () => {
  it("一家机场一个 tab，计数是这家的订阅数", async () => {
    const wrapper = await render();

    expect(wrapper.findComponent({ name: "ViewTabs" }).props("options")).toEqual([
      { value: 1, label: "泰山云", count: 2 },
      { value: 2, label: "某机场B", count: 1 },
    ]);
  });

  it("没带 ?airport 时选第一家，表里只有这家的订阅", async () => {
    const wrapper = await render();

    expect(subNames(wrapper)).toEqual(["taishan", "xxx"]);
  });

  it("?airport 指向某家机场时直接选它", async () => {
    route.query = { airport: "2" };
    const wrapper = await render();

    expect(subNames(wrapper)).toEqual(["b-main"]);
  });

  it("?airport 非法或指向已删除的机场时，安静地回落到第一家", async () => {
    route.query = { airport: "abc" };
    const first = await render();
    expect(subNames(first)).toEqual(["taishan", "xxx"]);
    first.unmount();

    route.query = { airport: "999" };
    const second = await render();
    expect(subNames(second)).toEqual(["taishan", "xxx"]);
  });

  it("切 tab 用 replace 改 ?airport，不往历史栈压记录", async () => {
    const wrapper = await render();

    await selectTab(wrapper, 2);

    expect(replace).toHaveBeenCalledWith({ query: { airport: "2" } });
    expect(push).not.toHaveBeenCalled();
    expect(subNames(wrapper)).toEqual(["b-main"]);
  });
});

describe("AirportSubscriptionsView 页头与机场信息条", () => {
  it("页头给整页规模：机场数、订阅数、全站主用名额", async () => {
    const wrapper = await render();

    const facts = wrapper.get(".page-facts").text().replace(/\s+/g, " ");
    expect(facts).toContain("共 2 家机场");
    expect(facts).toContain("3 个订阅");
    expect(facts).toContain("主用名额 14 / 60");
  });

  it("页头只有「尽调」「新建机场」", async () => {
    const wrapper = await render();

    expect(wrapper.findAll(".page-head-actions button").map((b) => b.text())).toEqual([
      "尽调",
      "新建机场",
    ]);
  });

  it("信息条展示当前机场的官网、备注与主用名额", async () => {
    const wrapper = await render();
    const bar = wrapper.get(".airport-bar");

    expect(bar.find('a[href="https://taishan.example.com"]').exists()).toBe(true);
    expect(bar.text()).toContain("老牌机场");
    expect(bar.text()).toContain("7 / 30");
  });

  it("「导入订阅」打开导入弹窗并预选当前机场", async () => {
    route.query = { airport: "2" };
    const wrapper = await render();

    await barButton(wrapper, "导入订阅").trigger("click");

    expect(wrapper.findComponent(SubImportModal).props()).toMatchObject({
      group: null,
      airportId: 2,
    });
  });

  it("「新建机场」不带待编辑记录，「编辑机场」带上当前机场", async () => {
    const wrapper = await render();

    await wrapper
      .findAll(".page-head-actions button")
      .find((b) => b.text() === "新建机场")!
      .trigger("click");
    expect(wrapper.findComponent(AirportFormModal).props("editing")).toBeNull();

    wrapper.findComponent(AirportFormModal).vm.$emit("close");
    await wrapper.vm.$nextTick();
    await barButton(wrapper, "编辑机场").trigger("click");
    expect(wrapper.findComponent(AirportFormModal).props("editing")).toMatchObject({ id: 1 });
  });

  it("机场表单 saved 后重新加载", async () => {
    const wrapper = await render();
    await barButton(wrapper, "编辑机场").trigger("click");

    wrapper.findComponent(AirportFormModal).vm.$emit("saved");
    await flushPromises();

    expect(listAirports).toHaveBeenCalledTimes(2);
    expect(listAirportSubscriptions).toHaveBeenCalledTimes(2);
  });

  it("删除机场：确认后删，成功后重载且回落到剩下的第一家", async () => {
    route.query = { airport: "2" };
    const wrapper = await render();
    await barButton(wrapper, "删除机场").trigger("click");
    expect(String(wrapper.findComponent(ConfirmDialog).props("message"))).toContain("某机场B");

    listAirports.mockResolvedValue([AIRPORTS[0]]);
    listAirportSubscriptions.mockResolvedValue(SUBS.filter((s) => s.airportId === 1));
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(deleteAirport).toHaveBeenCalledWith(2);
    expect(showToast).toHaveBeenCalledWith("success", "已删除机场");
    expect(subNames(wrapper)).toEqual(["taishan", "xxx"]);
  });

  it("机场下还有订阅删不掉时，用服务端那句中文", async () => {
    deleteAirport.mockRejectedValueOnce(new BizError(410053, "机场下还有订阅，不能删除"));
    const wrapper = await render();
    await barButton(wrapper, "删除机场").trigger("click");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "机场下还有订阅，不能删除");
  });

  it("「尽调」打开尽调弹窗，关掉后不重载（只读探测）", async () => {
    const wrapper = await render();
    await wrapper
      .findAll(".page-head-actions button")
      .find((b) => b.text() === "尽调")!
      .trigger("click");
    expect(wrapper.findComponent(AirportSubscriptionAuditModal).exists()).toBe(true);
    listAirports.mockClear();

    wrapper.findComponent(AirportSubscriptionAuditModal).vm.$emit("close");
    await wrapper.vm.$nextTick();

    expect(wrapper.findComponent(AirportSubscriptionAuditModal).exists()).toBe(false);
    expect(listAirports).not.toHaveBeenCalled();
  });
});

describe("AirportSubscriptionsView 订阅表", () => {
  it("列：订阅名、账号、带宽、主用名额、流量用量、到期、最近拉取；没有操作列", async () => {
    const wrapper = await render();

    expect(wrapper.findAll("thead th").map((th) => th.text())).toEqual([
      "订阅名",
      "账号",
      "带宽",
      "主用名额",
      "流量用量",
      "到期",
      "最近拉取",
    ]);
    expect(wrapper.findAll("tbody tr")[0].text()).toContain("0 / 15");
    expect(wrapper.findAll("tbody tr")[0].text()).toContain("64%");
  });

  it("流量没额度头时写「未提供」，不是 0%", async () => {
    listAirportSubscriptions.mockResolvedValue([sub({ usedBytes: null, totalBytes: null })]);
    const wrapper = await render();

    expect(wrapper.findAll("tbody tr")[0].text()).toContain("未提供");
    expect(wrapper.findAll("tbody tr")[0].text()).not.toContain("0%");
  });

  it("拉取失败的订阅：最近拉取一格换成「拉取失败」，悬停给出起始时间与原因", async () => {
    listAirportSubscriptions.mockResolvedValue([
      sub({ fetchFailedSince: "2026-09-29T06:00:00Z", lastFetchError: "HTTP 403" }),
    ]);
    const wrapper = await render();
    const failed = wrapper.get("tbody .fetch-failed");

    expect(failed.text()).toBe("拉取失败");
    expect(failed.attributes("title")).toContain("HTTP 403");
  });

  it("点整行进入订阅详情", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[1].trigger("click");

    expect(push).toHaveBeenCalledWith({ name: "SUBSCRIPTION_DETAIL", params: { id: 101 } });
  });

  it("点订阅名链接时由链接自己导航，行点击不再重复 push", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].get("a").trigger("click");

    expect(push).not.toHaveBeenCalled();
  });
});

describe("AirportSubscriptionsView 空态", () => {
  it("一家机场都没有：不画 tab 和信息条，引导先建机场", async () => {
    listAirports.mockResolvedValue([]);
    listAirportSubscriptions.mockResolvedValue([]);
    const wrapper = await render();

    expect(wrapper.findComponent({ name: "ViewTabs" }).exists()).toBe(false);
    expect(wrapper.find(".airport-bar").exists()).toBe(false);
    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toContain("还没有机场");
    expect(wrapper.findAll(".card-state-actions button").map((b) => b.text())).toEqual([
      "新建机场",
    ]);
  });

  it("当前机场没有订阅：提示并给「导入订阅」", async () => {
    route.query = { airport: "2" };
    listAirportSubscriptions.mockResolvedValue(SUBS.filter((s) => s.airportId === 1));
    const wrapper = await render();

    expect(String(wrapper.findComponent(DataCard).props("emptyText"))).toBe(
      "这家机场还没有订阅。",
    );
    expect(wrapper.findAll(".card-state-actions button").map((b) => b.text())).toEqual([
      "导入订阅",
    ]);
  });

  it("加载失败时把原因交给数据卡", async () => {
    listAirports.mockRejectedValue(new BizError(110001, "服务开小差"));
    const wrapper = await render();

    expect(wrapper.findComponent(DataCard).props("error")).toBe("服务开小差");
  });
});
