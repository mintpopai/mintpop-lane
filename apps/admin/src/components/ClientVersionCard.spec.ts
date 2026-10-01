import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ClientVersionStatus } from "../api/types";
import ClientVersionCard from "./ClientVersionCard.vue";

const getClientVersion = vi.fn<() => Promise<ClientVersionStatus>>();
const refreshClientVersion = vi.fn<() => Promise<ClientVersionStatus>>();
const showToast = vi.fn();

vi.mock("../api", () => ({
  adminApi: () => ({ getClientVersion, refreshClientVersion }),
}));
vi.mock("../toast", () => ({ showToast: (...args: unknown[]) => showToast(...args) }));

function status(overrides: Partial<ClientVersionStatus> = {}): ClientVersionStatus {
  return {
    latest: "1.1.0",
    fetchedAt: "2026-10-01T03:00:00Z",
    lastAttemptAt: "2026-10-01T03:00:00Z",
    lastAttemptFailed: false,
    manifestUrl: "https://dl.example.com/lane/latest.json",
    ...overrides,
  };
}

const refreshButton = (wrapper: ReturnType<typeof mount>) => wrapper.find("button.refresh");

beforeEach(() => {
  vi.clearAllMocks();
  getClientVersion.mockResolvedValue(status());
});

describe("ClientVersionCard", () => {
  it("载入后展示最新版本与清单地址", async () => {
    const wrapper = mount(ClientVersionCard);
    await flushPromises();
    expect(wrapper.text()).toContain("v1.1.0");
    expect(wrapper.text()).toContain("https://dl.example.com/lane/latest.json");
    expect(wrapper.text()).not.toContain("最近一次拉取失败");
  });

  it("从未拉到过：明确显示「尚未拉取到」", async () => {
    getClientVersion.mockResolvedValue(status({ latest: null, fetchedAt: null }));
    const wrapper = mount(ClientVersionCard);
    await flushPromises();
    expect(wrapper.text()).toContain("尚未拉取到");
  });

  it("最近一次拉取失败：标出来，版本仍显示沿用的那个", async () => {
    getClientVersion.mockResolvedValue(status({ lastAttemptFailed: true }));
    const wrapper = mount(ClientVersionCard);
    await flushPromises();
    expect(wrapper.text()).toContain("v1.1.0");
    expect(wrapper.text()).toContain("最近一次拉取失败");
  });

  it("立即拉取到新版：刷新展示并提示版本已更新", async () => {
    refreshClientVersion.mockResolvedValue(status({ latest: "1.2.0" }));
    const wrapper = mount(ClientVersionCard);
    await flushPromises();
    await refreshButton(wrapper).trigger("click");
    await flushPromises();

    expect(refreshClientVersion).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("v1.2.0");
    expect(showToast).toHaveBeenCalledWith("success", "最新版本已更新为 v1.2.0");
  });

  it("立即拉取版本没变：提示已是最新", async () => {
    refreshClientVersion.mockResolvedValue(status());
    const wrapper = mount(ClientVersionCard);
    await flushPromises();
    await refreshButton(wrapper).trigger("click");
    await flushPromises();
    expect(showToast).toHaveBeenCalledWith("success", "已是最新：v1.1.0");
  });

  it("立即拉取失败：提示沿用上一次的版本", async () => {
    refreshClientVersion.mockResolvedValue(status({ lastAttemptFailed: true }));
    const wrapper = mount(ClientVersionCard);
    await flushPromises();
    await refreshButton(wrapper).trigger("click");
    await flushPromises();
    expect(showToast).toHaveBeenCalledWith("error", "拉取失败，仍沿用 v1.1.0");
  });

  it("加载失败：显示错误，不给拉取按钮可点", async () => {
    getClientVersion.mockRejectedValue(new Error("网络错误"));
    const wrapper = mount(ClientVersionCard);
    await flushPromises();
    expect(wrapper.text()).toContain("网络错误");
    expect(refreshButton(wrapper).attributes("disabled")).toBeDefined();
  });
});
