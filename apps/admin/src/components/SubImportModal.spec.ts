import { DOMWrapper, flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AirportResponse, AirportSubscriptionResponse } from "../api/types";
import SubImportModal from "./SubImportModal.vue";

const createAirportSubscription = vi.fn(async () => 1);
const importAirportSubscription = vi.fn(async () => undefined);
const listAirports = vi.fn<() => Promise<AirportResponse[]>>();
const showToast = vi.fn();

vi.mock("../api", () => ({
  adminApi: () => ({ createAirportSubscription, importAirportSubscription, listAirports }),
}));
vi.mock("../toast", () => ({ showToast: (...args: unknown[]) => showToast(...args) }));

function airport(overrides: Partial<AirportResponse> = {}): AirportResponse {
  return {
    id: 1,
    name: "泰山云",
    websiteUrl: null,
    remark: null,
    subscriptionCount: 1,
    primaryUsed: 0,
    primaryCapacity: 0,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
    ...overrides,
  };
}

function subscription(
  overrides: Partial<AirportSubscriptionResponse> = {},
): AirportSubscriptionResponse {
  return {
    id: 7,
    name: "机场A",
    subUrlMasked: "https://sub.example.com/***",
    nodeCount: 5,
    remark: null,
    airportId: 1,
    airportName: "泰山云",
    account: "a@x.com",
    bandwidthMbps: 300,
    usedBytes: null,
    totalBytes: null,
    expiresAt: null,
    fetchedAt: null,
    primaryUsed: 0,
    primaryCapacity: 15,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  listAirports.mockResolvedValue([airport()]);
  // jsdom 不实现 scrollIntoView，AdminSelect 展开面板定位高亮项时会调它
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  // attachTo: document.body 会把弹窗真实挂到 body 上，测试间要清掉，不然下一次挂载会撞上上一次残留的 DOM
  document.body.innerHTML = "";
});

/** AdminModal 用 Teleport 挂到 body，游离节点里的内容 wrapper.find 够不着，须从 document 上取 */
function query(selector: string): DOMWrapper<Element> {
  const el = document.querySelector(selector);
  if (!el) {
    throw new Error(`未找到元素：${selector}`);
  }
  return new DOMWrapper(el);
}

/** 底部提交按钮是 footer 里的最后一个 admin-btn */
function submitButton(): DOMWrapper<Element> {
  const buttons = Array.from(document.querySelectorAll("button.admin-btn"));
  const last = buttons.at(-1);
  if (!last) {
    throw new Error("未找到提交按钮");
  }
  return new DOMWrapper(last);
}

function mountModal(group: AirportSubscriptionResponse | null) {
  return mount(SubImportModal, { attachTo: document.body, props: { group } });
}

/** 展开「所属机场」下拉，点中文案含 text 的那一项；面板 Teleport 到 body，选项从 document 上取 */
async function pickAirport(text: string): Promise<void> {
  await query('button[aria-label="所属机场"]').trigger("click");
  const option = Array.from(document.querySelectorAll("li")).find((li) =>
    li.textContent?.includes(text),
  );
  if (!option) {
    throw new Error(`机场选项未找到：${text}`);
  }
  await new DOMWrapper(option).trigger("click");
}

describe("SubImportModal", () => {
  it("创建模式：链接、订阅名、备注一屏填完，没有节点勾选列表", async () => {
    mountModal(null);
    await flushPromises();

    expect(document.querySelector("#sub-url")).not.toBeNull();
    expect(document.querySelector("#group-name")).not.toBeNull();
    expect(document.querySelector("#group-remark")).not.toBeNull();
    expect(document.querySelector("table")).toBeNull();
    expect(document.body.textContent).toContain("自动导入订阅里的美国节点");
  });

  it("创建模式：选机场、填账号与带宽后一次提交", async () => {
    const wrapper = mountModal(null);
    await flushPromises();
    await query("#sub-url").setValue("https://sub.example.com/c?token=t");
    await query("#group-name").setValue("ts-01");
    await pickAirport("泰山云");
    await query("#sub-account").setValue("a@x.com");
    await query("#sub-bandwidth").setValue("300");
    await submitButton().trigger("click");

    await vi.waitFor(() =>
      expect(createAirportSubscription).toHaveBeenCalledWith({
        name: "ts-01",
        subUrl: "https://sub.example.com/c?token=t",
        airportId: 1,
        account: "a@x.com",
        bandwidthMbps: 300,
        remark: "",
      }),
    );
    expect(wrapper.emitted("saved")).toBeTruthy();
  });

  it("没有机场时提示先建机场，不给提交", async () => {
    listAirports.mockResolvedValueOnce([]);
    mountModal(null);
    await flushPromises();

    expect(document.body.textContent).toContain("还没有机场");
    expect(document.querySelector("button.admin-btn:disabled")).not.toBeNull();
  });

  it("加载机场失败时提示加载失败，不误判为还没有机场", async () => {
    listAirports.mockRejectedValueOnce(new BizError(500, "机场服务暂不可用"));
    mountModal(null);
    await flushPromises();

    expect(showToast).toHaveBeenCalledWith("error", "加载机场失败：机场服务暂不可用");
    expect(document.body.textContent).not.toContain("还没有机场");
  });

  it("带宽不是正整数时不提交", async () => {
    mountModal(null);
    await flushPromises();
    await query("#sub-url").setValue("https://sub.example.com/c?token=t");
    await query("#group-name").setValue("ts-01");
    await pickAirport("泰山云");
    await query("#sub-account").setValue("a@x.com");
    await query("#sub-bandwidth").setValue("0");
    await submitButton().trigger("click");

    expect(showToast).toHaveBeenLastCalledWith("error", "带宽填大于 0 的整数（Mbps）");
    expect(createAirportSubscription).not.toHaveBeenCalled();
  });

  it("缺链接或缺订阅名时不发请求，直接提示", async () => {
    mountModal(null);
    await flushPromises();
    await submitButton().trigger("click");
    expect(showToast).toHaveBeenLastCalledWith("error", "先粘贴订阅链接");

    await query("#sub-url").setValue("https://sub.example.com/c?token=t");
    await submitButton().trigger("click");
    expect(showToast).toHaveBeenLastCalledWith("error", "给这个订阅起个名字");

    expect(createAirportSubscription).not.toHaveBeenCalled();
  });

  it("订阅里没有美国节点时把服务端的说法原样提示，弹窗不关", async () => {
    createAirportSubscription.mockRejectedValueOnce(
      new BizError(410049, "订阅里没有美国节点（节点名带 🇺🇸 或 [US]），未导入"),
    );
    const wrapper = mountModal(null);
    await flushPromises();
    await query("#sub-url").setValue("https://sub.example.com/c?token=t");
    await query("#group-name").setValue("ts-01");
    await pickAirport("泰山云");
    await query("#sub-account").setValue("a@x.com");
    await query("#sub-bandwidth").setValue("300");
    await submitButton().trigger("click");

    await vi.waitFor(() =>
      expect(showToast).toHaveBeenCalledWith(
        "error",
        "订阅里没有美国节点（节点名带 🇺🇸 或 [US]），未导入",
      ),
    );
    expect(wrapper.emitted("close")).toBeFalsy();
  });

  it("重新拉取模式：不出现链接与订阅名输入框，一键调用 importAirportSubscription", async () => {
    const wrapper = mountModal(subscription());

    expect(document.querySelector("#sub-url")).toBeNull();
    expect(document.querySelector("#group-name")).toBeNull();
    await submitButton().trigger("click");

    await vi.waitFor(() => expect(importAirportSubscription).toHaveBeenCalledWith(7));
    expect(showToast).toHaveBeenCalledWith("success", "已重新拉取并导入美国节点");
    expect(wrapper.emitted("saved")).toBeTruthy();
  });
});
