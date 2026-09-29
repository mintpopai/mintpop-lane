import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { FrontRebuildPreview, FrontRebuildStatus, FrontSettingsResponse } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import SettingsView from "./SettingsView.vue";

const getFrontSettings = vi.fn<() => Promise<FrontSettingsResponse>>();
const updateFrontSettings = vi.fn(async (): Promise<FrontSettingsResponse> =>
  settings({ airportsPerUser: 2 }),
);
const frontRebuildStatus = vi.fn<() => Promise<FrontRebuildStatus>>();
const startFrontRebuild = vi.fn(async () => undefined);
const previewFrontRebuild = vi.fn<() => Promise<FrontRebuildPreview>>();
const showToast = vi.fn();

vi.mock("../api", () => ({
  adminApi: () => ({
    getFrontSettings,
    updateFrontSettings,
    frontRebuildStatus,
    startFrontRebuild,
    previewFrontRebuild,
  }),
}));
vi.mock("../toast", () => ({ showToast: (...args: unknown[]) => showToast(...args) }));

function settings(overrides: Partial<FrontSettingsResponse> = {}): FrontSettingsResponse {
  return {
    region: "US",
    regionOptions: [{ value: "US", label: "美国" }],
    airportsPerUser: 3,
    bandwidthPerUserMbps: 20,
    ...overrides,
  };
}

function idle(): FrontRebuildStatus {
  return {
    phase: "IDLE",
    startedAt: null,
    finishedAt: null,
    userCount: null,
    subscriptionCount: null,
    error: null,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  getFrontSettings.mockResolvedValue(settings());
  frontRebuildStatus.mockResolvedValue(idle());
  previewFrontRebuild.mockResolvedValue({
    requiredPrimary: 120,
    availablePrimary: 150,
    sufficient: true,
  });
});

afterEach(() => {
  vi.useRealTimers();
  document.body.innerHTML = "";
});

describe("SettingsView", () => {
  it("载入后显示三项设置，机场数提示「1 条主线路，2 条备用线路」，并常显容量预检", async () => {
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();

    expect((wrapper.find("#setting-airports").element as HTMLInputElement).value).toBe("3");
    expect((wrapper.find("#setting-bandwidth").element as HTMLInputElement).value).toBe("20");
    expect(wrapper.text()).toContain("1 条主线路，2 条备用线路");
    expect(wrapper.text()).toContain("120");
    expect(wrapper.text()).toContain("150");
  });

  it("改机场数为 1 时提示「1 条主线路，0 条备用线路」", async () => {
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();
    await wrapper.find("#setting-airports").setValue("1");

    expect(wrapper.text()).toContain("1 条主线路，0 条备用线路");
  });

  it("保存：先弹确认（带预检数字），确认后 PUT 并开始轮询状态", async () => {
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();
    await wrapper.find("#setting-airports").setValue("2");
    await wrapper.find("button.admin-btn.save").trigger("click");
    await flushPromises();

    const dialog = wrapper.findComponent(ConfirmDialog);
    expect(dialog.exists()).toBe(true);
    expect(dialog.props("message")).toContain("需要 120 个主用名额，现有 150");
    expect(updateFrontSettings).not.toHaveBeenCalled();

    frontRebuildStatus.mockResolvedValue({
      ...idle(),
      phase: "RUNNING",
      startedAt: "2026-09-29T03:00:00Z",
    });
    dialog.vm.$emit("confirm");
    await flushPromises();

    expect(updateFrontSettings).toHaveBeenCalledWith({
      region: "US",
      airportsPerUser: 2,
      bandwidthPerUserMbps: 20,
    });
    expect(showToast).toHaveBeenCalledWith("success", "已保存，正在为全部用户重算线路");
    expect(wrapper.text()).toContain("重算中");
  });

  it("预检不足时确认按钮禁用", async () => {
    previewFrontRebuild.mockResolvedValue({
      requiredPrimary: 120,
      availablePrimary: 90,
      sufficient: false,
    });
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();
    await wrapper.find("#setting-bandwidth").setValue("30");
    await wrapper.find("button.admin-btn.save").trigger("click");
    await flushPromises();

    const dialog = wrapper.findComponent(ConfirmDialog);
    expect(dialog.props("confirmDisabled")).toBe(true);
    expect(dialog.props("busy")).toBe(false);
    expect(wrapper.text()).toContain("需要 120 个主用名额，现有 90");
  });

  it("「重算全部线路」：确认后 POST", async () => {
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();
    await wrapper.find("button.rebuild").trigger("click");
    await flushPromises();
    wrapper.findComponent(ConfirmDialog).vm.$emit("confirm");
    await flushPromises();

    expect(startFrontRebuild).toHaveBeenCalled();
  });

  it("显示最近一次重算结果：成功带人数，失败带原因", async () => {
    frontRebuildStatus.mockResolvedValue({
      phase: "FAILED",
      startedAt: "2026-09-29T03:00:00Z",
      finishedAt: "2026-09-29T03:00:05Z",
      userCount: null,
      subscriptionCount: null,
      error: "主用名额不足，无法为全部用户分配线路：需要 3 个主用名额，现有 2",
    });
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();

    expect(wrapper.text()).toContain("上次重算失败");
    expect(wrapper.text()).toContain("需要 3 个主用名额，现有 2");
  });

  it("表单有未保存改动时「重算全部线路」禁用并给出提示", async () => {
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();
    expect(wrapper.find("button.rebuild").attributes("disabled")).toBeUndefined();

    await wrapper.find("#setting-bandwidth").setValue("30");

    expect(wrapper.find("button.rebuild").attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("先保存或还原改动后再重算");
  });

  it("有改动时出现「还原」，点了表单回到已保存的值、重算按钮恢复可点", async () => {
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();
    expect(wrapper.find("button.revert").exists()).toBe(false);

    await wrapper.find("#setting-bandwidth").setValue("30");
    await wrapper.find("button.revert").trigger("click");

    expect((wrapper.find("#setting-bandwidth").element as HTMLInputElement).value).toBe("20");
    expect(wrapper.find("button.revert").exists()).toBe(false);
    expect(wrapper.find("button.rebuild").attributes("disabled")).toBeUndefined();
  });

  it("手动重算的预检按已保存值算，不用表单值", async () => {
    const wrapper = mount(SettingsView, { attachTo: document.body });
    await flushPromises();
    previewFrontRebuild.mockClear();
    await wrapper.find("button.rebuild").trigger("click");
    await flushPromises();

    expect(previewFrontRebuild).toHaveBeenCalledWith("US", 20);
  });

  describe("状态轮询", () => {
    function running(): FrontRebuildStatus {
      return { ...idle(), phase: "RUNNING", startedAt: "2026-09-29T03:00:00Z" };
    }

    it("载入时 RUNNING 则每 3 秒轮询；变为 SUCCEEDED 后停止", async () => {
      vi.useFakeTimers();
      frontRebuildStatus.mockResolvedValue(running());
      const wrapper = mount(SettingsView, { attachTo: document.body });
      await flushPromises();
      expect(frontRebuildStatus).toHaveBeenCalledTimes(1);

      await vi.advanceTimersByTimeAsync(3000);
      expect(frontRebuildStatus).toHaveBeenCalledTimes(2);

      frontRebuildStatus.mockResolvedValue({
        ...idle(),
        phase: "SUCCEEDED",
        userCount: 1,
        subscriptionCount: 1,
      });
      await vi.advanceTimersByTimeAsync(3000);
      expect(frontRebuildStatus).toHaveBeenCalledTimes(3);

      await vi.advanceTimersByTimeAsync(9000);
      expect(frontRebuildStatus).toHaveBeenCalledTimes(3);
      wrapper.unmount();
    });

    it("空闲时不轮询", async () => {
      vi.useFakeTimers();
      const wrapper = mount(SettingsView, { attachTo: document.body });
      await flushPromises();
      await vi.advanceTimersByTimeAsync(9000);

      expect(frontRebuildStatus).toHaveBeenCalledTimes(1);
      wrapper.unmount();
    });

    it("卸载后不再轮询", async () => {
      vi.useFakeTimers();
      frontRebuildStatus.mockResolvedValue(running());
      const wrapper = mount(SettingsView, { attachTo: document.body });
      await flushPromises();
      wrapper.unmount();
      await vi.advanceTimersByTimeAsync(9000);

      expect(frontRebuildStatus).toHaveBeenCalledTimes(1);
    });
  });
});
