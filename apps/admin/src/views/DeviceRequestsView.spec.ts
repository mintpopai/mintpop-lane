import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AdminDeviceRebindRequestResponse, RebindRequestStatus } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import { useRebindStore } from "../stores/rebind";
import { showToast } from "../toast";
import DeviceRequestsView from "./DeviceRequestsView.vue";

// 签名与真接口一致（带可选状态参数）：角标 store 打的就是带 PENDING 的那一路，要能按参数区分
const listDeviceRebindRequests =
  vi.fn<(status?: RebindRequestStatus) => Promise<AdminDeviceRebindRequestResponse[]>>();
const approveDeviceRebindRequest = vi.fn<(id: number) => Promise<void>>(async () => undefined);
const rejectDeviceRebindRequest = vi.fn<(id: number) => Promise<void>>(async () => undefined);

vi.mock("../api", () => ({
  adminApi: () => ({
    listDeviceRebindRequests,
    approveDeviceRebindRequest,
    rejectDeviceRebindRequest,
  }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function request(
  overrides: Partial<AdminDeviceRebindRequestResponse> = {},
): AdminDeviceRebindRequestResponse {
  return {
    id: 1,
    requestNo: "DR20260915000001",
    subscriptionId: 11,
    subscriptionName: "Claude 月付",
    assignmentNo: "7K3M9QX2FT",
    userId: 3,
    userEmail: "u@example.com",
    fromDevice: {
      name: "旧机",
      os: "macos 15.6",
      model: "Mac15,6",
      lastSeenAt: "2026-09-15T09:00:00Z",
    },
    toDevice: {
      name: "新机",
      os: "macos 26.6",
      model: "Mac17,9",
      lastSeenAt: "2026-09-15T11:50:00Z",
    },
    reason: "换了新电脑",
    status: "PENDING",
    createdAt: "2026-09-15T02:00:00Z",
    decidedAt: null,
    ...overrides,
  };
}

const ROWS = [
  request({ id: 1, status: "PENDING" }),
  request({ id: 2, status: "PENDING", userEmail: "b@example.com" }),
  request({ id: 3, status: "APPROVED", decidedAt: "2026-09-14T02:00:00Z" }),
];

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
  listDeviceRebindRequests.mockResolvedValue(ROWS);
});

afterEach(() => {
  document.body.innerHTML = "";
});

async function render() {
  const wrapper = mount(DeviceRequestsView, { attachTo: document.body });
  await flushPromises();
  return wrapper;
}

function rowCount(wrapper: Awaited<ReturnType<typeof render>>): number {
  return wrapper.findAll("tbody tr").length;
}

/** 列表重拉的次数。角标 store 打的是同一个 mock（带 PENDING），故按「无参调用」计数 */
function listLoadCount(): number {
  return listDeviceRebindRequests.mock.calls.filter((call) => call.length === 0).length;
}

/** 角标刷新的次数：带 PENDING 的那些调用只可能来自 store.refresh() */
function badgeRefreshCount(): number {
  return listDeviceRebindRequests.mock.calls.filter((call) => call[0] === "PENDING").length;
}

describe("DeviceRequestsView 加载", () => {
  it("一次拉全部申请，默认只铺待处理的那些", async () => {
    const wrapper = await render();

    expect(listDeviceRebindRequests).toHaveBeenCalledWith();
    expect(rowCount(wrapper)).toBe(2);
  });

  it("切到「全部」能看到已处理的历史", async () => {
    const wrapper = await render();

    wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", "ALL");
    await wrapper.vm.$nextTick();

    expect(rowCount(wrapper)).toBe(3);
  });

  it("两档的计数都取自同一批数据", async () => {
    const wrapper = await render();

    expect(wrapper.findComponent({ name: "ViewTabs" }).props("options")).toEqual([
      { value: "PENDING", label: "待处理", count: 2 },
      { value: "ALL", label: "全部", count: 3 },
    ]);
  });

  it("拉取失败时把服务端那句中文交给数据卡", async () => {
    listDeviceRebindRequests.mockRejectedValueOnce(new BizError(410001, "没权限看申请"));
    const wrapper = await render();

    expect(wrapper.findComponent(DataCard).props("error")).toBe("没权限看申请");
  });

  it("原设备记录已不存在时如实显示，不画成空格或「未绑定」", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([request({ fromDevice: null })]);
    const wrapper = await render();

    expect(wrapper.get("tbody tr").text()).toContain("设备记录已不存在");
  });

  it("新设备记录事后被删除时同样如实显示「设备记录已不存在」", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([request({ toDevice: null })]);
    const wrapper = await render();

    expect(wrapper.get("tbody tr").text()).toContain("设备记录已不存在");
  });

  it("订阅已被删除（空字符串）时隐藏订阅与分配号，而不是显示占位符", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([
      request({ subscriptionName: "", assignmentNo: "" }),
    ]);
    const wrapper = await render();

    const subscriptionCell = wrapper.get("tbody tr").findAll("td")[1];
    expect(subscriptionCell.text()).toBe("");
  });
});

describe("DeviceRequestsView 处理申请", () => {
  it("同意要先过确认框，确认后调接口并重拉列表", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll("td.actions button")[0].trigger("click");
    expect(wrapper.findComponent(ConfirmDialog).props("message")).toContain("7K3M9-QX2FT");

    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(approveDeviceRebindRequest).toHaveBeenCalledWith(1);
    expect(listLoadCount()).toBe(2);
    expect(badgeRefreshCount()).toBe(1);
    expect(showToast).toHaveBeenCalledWith("success", "已同意，用户刷新席位后即可使用");
  });

  it("拒绝走同一套确认，调的是拒绝接口", async () => {
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll("td.actions button")[1].trigger("click");
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(rejectDeviceRebindRequest).toHaveBeenCalledWith(1);
    expect(approveDeviceRebindRequest).not.toHaveBeenCalled();
  });

  // 断网、5xx 这类失败与「这行过期了」是两回事：列表多半还是准的，再拉一次只会把一个失败叠成两个
  it("网络类失败原样转述报错，列表与角标都不重拉", async () => {
    approveDeviceRebindRequest.mockRejectedValueOnce(new Error("Failed to fetch"));
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll("td.actions button")[0].trigger("click");
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "Failed to fetch");
    expect(listLoadCount()).toBe(1);
    expect(badgeRefreshCount()).toBe(0);
  });

  // 确认框留在原地就等于「只能对着同一行再点一次、再得到同一句报错」，是条死路
  it("任何失败都收起确认框，不把它悬在刚拒绝了这次操作的那行上", async () => {
    approveDeviceRebindRequest.mockRejectedValueOnce(new Error("Failed to fetch"));
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll("td.actions button")[0].trigger("click");
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(false);
  });

  // 服务端明说「这条申请没了 / 已被别人处理 / 订阅已删」时，界面上摆着的这行可证是错的
  it.each([
    [410042, "换机申请不存在"],
    [410043, "该换机申请已被处理"],
    [410008, "订阅不存在"],
  ])("服务端回 %i 说明本机这份已过期，列表与角标一并重拉", async (code, message) => {
    approveDeviceRebindRequest.mockRejectedValueOnce(new BizError(code, message));
    const wrapper = await render();
    const store = useRebindStore();

    await wrapper.findAll("tbody tr")[0].findAll("td.actions button")[0].trigger("click");
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", message);
    expect(wrapper.findComponent(ConfirmDialog).exists()).toBe(false);
    expect(listLoadCount()).toBe(2);
    expect(badgeRefreshCount()).toBe(1);
    expect(store.pendingCount).toBe(ROWS.length);
  });

  it("已处理的申请不给操作按钮", async () => {
    const wrapper = await render();
    wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", "ALL");
    await wrapper.vm.$nextTick();

    const historyRow = wrapper.findAll("tbody tr")[2];

    expect(historyRow.findAll("td.actions button")).toHaveLength(0);
  });

  it("即便订阅已被删除（服务端会拒绝），仍照常给出操作按钮，不由前端猜测特判", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([
      request({ subscriptionName: "", assignmentNo: "" }),
    ]);
    const wrapper = await render();

    expect(wrapper.get("tbody tr").findAll("td.actions button")).toHaveLength(2);
  });

  it("订阅已被删除时，确认框不拼出空书名号或分配号占位符「—」", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([
      request({ subscriptionName: "", assignmentNo: "" }),
    ]);
    const wrapper = await render();

    await wrapper.get("tbody tr").findAll("td.actions button")[0].trigger("click");
    const message = wrapper.findComponent(ConfirmDialog).props("message");

    expect(message).not.toContain("—");
    expect(message).not.toContain("「」");
  });
});

describe("DeviceRequestsView 页头刷新", () => {
  it("页头的「刷新」把列表与角标一起重拉——怀疑数据过期时不必切走再切回来", async () => {
    const wrapper = await render();
    const store = useRebindStore();

    const refreshButton = wrapper
      .findAll(".page-head-actions button")
      .find((button) => button.text() === "刷新");
    await refreshButton!.trigger("click");
    await flushPromises();

    expect(listLoadCount()).toBe(2);
    expect(badgeRefreshCount()).toBe(1);
    expect(store.pendingCount).toBe(ROWS.length);
  });
});

describe("DeviceRequestsView 展示细节", () => {
  it("机型为空时不留下吊着的分隔符", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([
      request({
        toDevice: {
          name: "DESKTOP-4F2",
          os: "windows 11",
          model: "",
          lastSeenAt: "2026-09-15T11:50:00Z",
        },
      }),
    ]);
    const wrapper = await render();

    // 只盯设备标签本身，不锁死整个单元格——同格里还有「最近活跃」那一行
    const cell = wrapper.get("tbody tr").findAll("td")[3];
    expect(cell.text()).toContain("DESKTOP-4F2（windows 11）");
    expect(cell.text()).not.toContain("· ");
  });

  // 绿色在本仓的色板里只表示「在跑 / 正常」，已拒绝与已作废都不是
  it("只有已同意走绿档，已拒绝与已作废归灰档", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([
      request({ id: 1, status: "APPROVED" }),
      request({ id: 2, status: "REJECTED" }),
      request({ id: 3, status: "SUPERSEDED" }),
      request({ id: 4, status: "PENDING" }),
    ]);
    const wrapper = await render();
    wrapper.findComponent({ name: "ViewTabs" }).vm.$emit("update:modelValue", "ALL");
    await wrapper.vm.$nextTick();

    const states = wrapper.findAll("tbody tr .state").map((el) => el.attributes("data-state"));
    expect(states).toEqual(["ENABLED", "CLOSED", "CLOSED", "MISSING"]);
  });

  // 飞书卡片拿申请号当把手；订阅被删的申请上「用户 + 分配号」都是空的，只剩它能对上号
  it("每行都渲染出申请号，订阅已被删除时同样看得见", async () => {
    listDeviceRebindRequests.mockResolvedValueOnce([
      request({ requestNo: "DR20260915000007", subscriptionName: "", assignmentNo: "" }),
    ]);
    const wrapper = await render();

    expect(wrapper.get("tbody tr").text()).toContain("DR20260915000007");
  });

  it("两台设备各自带上最近活跃时刻：原设备刚刚还在用，这次申请多半不是真换机", async () => {
    // 相对说法是按「现在」算的，钉住它才能断言
    vi.spyOn(Date, "now").mockReturnValue(new Date("2026-09-15T12:00:00Z").getTime());
    const wrapper = await render();

    const row = wrapper.get("tbody tr").text();
    expect(row).toContain("3 小时前");
    expect(row).toContain("10 分钟前");
  });
});
