import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { SubAuditResponse } from "../api/types";
import { showToast } from "../toast";
import NodeGroupAuditModal from "./NodeGroupAuditModal.vue";

const auditNodeGroup = vi.fn<(body: { subUrl: string }) => Promise<SubAuditResponse>>();

vi.mock("../api", () => ({
  adminApi: () => ({ auditNodeGroup }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function auditReport(overrides: Partial<SubAuditResponse> = {}): SubAuditResponse {
  return {
    airportName: "候选机场",
    totalNodes: 10,
    usNodeCount: 0,
    usNodeNames: [],
    failureDomains: [],
    conflictsWith: [],
    protocols: [],
    usedBytes: null,
    totalBytes: null,
    expiresAt: null,
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  // AdminModal 用 Teleport 挂到 body，测试间要清掉，不然下一次挂载撞上上一次残留的 DOM
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

/** 贴链接、点「开始尽调」，等报告回来 */
async function submitAuditWith(overrides: Partial<SubAuditResponse>) {
  auditNodeGroup.mockResolvedValueOnce(auditReport(overrides));
  const wrapper = mount(NodeGroupAuditModal, { attachTo: document.body });
  await query("#audit-sub-url").setValue("https://example.com/sub");
  await query("button.admin-btn").trigger("click");
  await vi.waitFor(() => expect(auditNodeGroup).toHaveBeenCalledTimes(1));
  return wrapper;
}

describe("NodeGroupAuditModal", () => {
  it("贴链接提交后，把订阅链接原样传给尽调接口", async () => {
    await submitAuditWith({});

    expect(auditNodeGroup).toHaveBeenCalledWith({ subUrl: "https://example.com/sub" });
  });

  it("链接为空时不发请求，提示先粘贴链接", async () => {
    mount(NodeGroupAuditModal, { attachTo: document.body });

    await query("button.admin-btn").trigger("click");

    expect(auditNodeGroup).not.toHaveBeenCalled();
    expect(showToast).toHaveBeenCalledWith("error", "先粘贴候选机场的订阅链接");
  });

  it("conflictsWith 非空时用醒目的否决态样式标出，不是和其它文案一样平铺", async () => {
    await submitAuditWith({ conflictsWith: ["TaiShan Net"] });

    expect(document.body.textContent).toContain("与现有分组同故障域");
    const verdict = document.querySelector(".audit-verdict-danger");
    expect(verdict).not.toBeNull();
    expect(verdict!.textContent).toContain("建议否决");
    expect(verdict!.textContent).toContain("TaiShan Net");
  });

  it("未撞库时给出正面结论，不是留白", async () => {
    await submitAuditWith({ conflictsWith: [] });

    expect(document.querySelector(".audit-verdict-danger")).toBeNull();
    expect(document.querySelector(".audit-verdict-ok")!.textContent).toContain("未发现");
  });

  it("完整列出启发式判定的美国节点供人核对，并注明是启发式判断", async () => {
    await submitAuditWith({ usNodeNames: ["🇺🇸[US]San Jose07", "United States 03"] });

    expect(document.body.textContent).toContain("🇺🇸[US]San Jose07");
    expect(document.body.textContent).toContain("United States 03");
    expect(document.body.textContent).toContain("判定为启发式");
  });

  it("没有疑似美国节点时明确说清楚，不是留白列表", async () => {
    await submitAuditWith({ usNodeNames: [] });

    expect(document.body.textContent).toContain("未发现疑似美国节点");
  });

  it("尽调失败时用服务端中文提示，不吞掉错误", async () => {
    auditNodeGroup.mockRejectedValueOnce(new BizError(410099, "订阅拉取失败"));
    mount(NodeGroupAuditModal, { attachTo: document.body });

    await query("#audit-sub-url").setValue("https://example.com/sub");
    await query("button.admin-btn").trigger("click");

    await vi.waitFor(() => expect(showToast).toHaveBeenCalledWith("error", "订阅拉取失败"));
  });

  it("报告回来后弹窗切宽款，且只剩「关闭」按钮，不再能重复提交", async () => {
    await submitAuditWith({});

    expect(document.querySelector(".dialog.wide")).not.toBeNull();
    expect(document.querySelectorAll("footer button")).toHaveLength(1);
    expect(document.querySelector("footer button")!.textContent).toContain("关闭");
  });
});
