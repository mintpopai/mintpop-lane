import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AdminDeviceRebindRequestResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import { showToast } from "../toast";
import DeviceRequestsView from "./DeviceRequestsView.vue";

const listDeviceRebindRequests = vi.fn<() => Promise<AdminDeviceRebindRequestResponse[]>>();
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
    fromDevice: { name: "旧机", os: "macos 15.6", model: "Mac15,6" },
    toDevice: { name: "新机", os: "macos 26.6", model: "Mac17,9" },
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
    // 角标 store 也打同一个 mock（带 PENDING），故按「无参调用」计数才看得出列表重拉了一次
    expect(listDeviceRebindRequests.mock.calls.filter((call) => call.length === 0)).toHaveLength(2);
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

  it("接口失败时原样转述服务端的中文，列表不重拉", async () => {
    approveDeviceRebindRequest.mockRejectedValueOnce(new BizError(410044, "该申请已被处理"));
    const wrapper = await render();

    await wrapper.findAll("tbody tr")[0].findAll("td.actions button")[0].trigger("click");
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "该申请已被处理");
    expect(listDeviceRebindRequests.mock.calls.filter((call) => call.length === 0)).toHaveLength(1);
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
