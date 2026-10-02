import { DOMWrapper, flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AirportResponse, AirportSubscriptionResponse } from "../api/types";
import FrontManualModal from "./FrontManualModal.vue";

const listAirports = vi.fn<() => Promise<AirportResponse[]>>();
const listAirportSubscriptions = vi.fn<() => Promise<AirportSubscriptionResponse[]>>();
const assignUserFrontManually = vi.fn(async () => []);
const showToast = vi.fn();

vi.mock("../api", () => ({
  adminApi: () => ({ listAirports, listAirportSubscriptions, assignUserFrontManually }),
}));
vi.mock("../toast", () => ({ showToast: (...args: unknown[]) => showToast(...args) }));

function airport(id: number, name: string, primaryEnabled = true): AirportResponse {
  return {
    id,
    name,
    websiteUrl: null,
    remark: null,
    primaryEnabled,
    subscriptionCount: 1,
    primaryUsed: 0,
    primaryCapacity: 0,
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
  };
}

function sub(
  id: number,
  airportId: number,
  airportName: string,
  overrides: Partial<AirportSubscriptionResponse> = {},
): AirportSubscriptionResponse {
  return {
    id,
    name: `${airportName}-01`,
    subUrlMasked: "https://x/***",
    nodeCount: 3,
    remark: null,
    airportId,
    airportName,
    account: "a@b.c",
    bandwidthMbps: 300,
    usedBytes: null,
    totalBytes: null,
    expiresAt: null,
    fetchedAt: null,
    fetchFailedSince: null,
    lastFetchError: null,
    primaryUsed: 0,
    primaryCapacity: 15,
    createdAt: "2026-10-01T00:00:00Z",
    updatedAt: "2026-10-01T00:00:00Z",
    ...overrides,
  };
}

/** 按 aria-label 找到某一行的下拉，读它的选项 */
function selectOf(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper
    .findAllComponents({ name: "AdminSelect" })
    .find((c) => c.props("ariaLabel") === label)!;
}

function optionValues(wrapper: ReturnType<typeof mount>, label: string): unknown[] {
  return (selectOf(wrapper, label).props("options") as { value: unknown }[]).map((o) => o.value);
}

// 弹窗 Teleport 到 body，DOM 断言一律从 document 取
function saveButton(): DOMWrapper<Element> {
  return new DOMWrapper(Array.from(document.querySelectorAll("button.admin-btn")).at(-1)!);
}

function button(text: string): DOMWrapper<Element> {
  return new DOMWrapper(
    Array.from(document.querySelectorAll("button")).find((b) => b.textContent?.trim() === text)!,
  );
}

function bodyText(): string {
  return document.body.textContent ?? "";
}

beforeEach(() => {
  vi.clearAllMocks();
  Element.prototype.scrollIntoView = vi.fn();
  listAirports.mockResolvedValue([
    airport(1, "泰山云"),
    airport(2, "华山云", false),
    airport(3, "空云"),
  ]);
  listAirportSubscriptions.mockResolvedValue([
    sub(11, 1, "泰山云", { primaryUsed: 15, primaryCapacity: 15 }),
    sub(21, 2, "华山云"),
    sub(31, 3, "空云", { nodeCount: 0 }),
  ]);
});

afterEach(() => {
  document.body.innerHTML = "";
});

describe("FrontManualModal", () => {
  it("主用只列主用机场且有节点的订阅；备用排除已被其它行占用的机场", async () => {
    const wrapper = mount(FrontManualModal, {
      attachTo: document.body,
      props: { userId: 5, current: [] },
    });
    await flushPromises();

    expect(optionValues(wrapper, "主用订阅")).toEqual([11]);
    selectOf(wrapper, "主用订阅").vm.$emit("update:modelValue", 11);
    await button("添加备用").trigger("click");

    expect(optionValues(wrapper, "备用1订阅")).toEqual([21]);
  });

  it("主用名额已满仍可保存，但提示会超额；提交按顺位 PUT", async () => {
    const wrapper = mount(FrontManualModal, {
      attachTo: document.body,
      props: { userId: 5, current: [] },
    });
    await flushPromises();

    selectOf(wrapper, "主用订阅").vm.$emit("update:modelValue", 11);
    await button("添加备用").trigger("click");
    selectOf(wrapper, "备用1订阅").vm.$emit("update:modelValue", 21);
    await flushPromises();

    expect(bodyText()).toContain("主用名额已满（15/15）");
    await saveButton().trigger("click");
    await flushPromises();

    expect(assignUserFrontManually).toHaveBeenCalledWith(5, [11, 21]);
    expect(wrapper.emitted("saved")).toBeTruthy();
  });

  it("重存自己当前的主用不算超额：名额里扣掉他自己", async () => {
    mount(FrontManualModal, {
      attachTo: document.body,
      props: {
        userId: 5,
        current: [
          {
            position: 0,
            airportSubscriptionId: 11,
            airportName: "泰山云",
            subscriptionName: "泰山云-01",
            account: "a@b.c",
          },
        ],
      },
    });
    await flushPromises();

    expect(bodyText()).not.toContain("主用名额已满");
  });

  it("有未选的行时不能保存", async () => {
    mount(FrontManualModal, {
      attachTo: document.body,
      props: { userId: 5, current: [] },
    });
    await flushPromises();

    expect(saveButton().attributes("disabled")).toBeDefined();
  });

  it("上移：备用与主用对调，主用行原先的仅备用机场不可选时提示重选", async () => {
    const wrapper = mount(FrontManualModal, {
      attachTo: document.body,
      props: {
        userId: 5,
        current: [
          {
            position: 0,
            airportSubscriptionId: 11,
            airportName: "泰山云",
            subscriptionName: "泰山云-01",
            account: "a@b.c",
          },
          {
            position: 1,
            airportSubscriptionId: 21,
            airportName: "华山云",
            subscriptionName: "华山云-01",
            account: "a@b.c",
          },
        ],
      },
    });
    await flushPromises();

    await new DOMWrapper(document.querySelector('button[aria-label="备用1上移"]')!).trigger(
      "click",
    );

    expect(selectOf(wrapper, "主用订阅").props("modelValue")).toBe(21);
    expect(bodyText()).toContain("主用原先的订阅已不可选");
    expect(saveButton().attributes("disabled")).toBeDefined();
  });
});
