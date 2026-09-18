import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { SubAuditFailureDomainReport, SubAuditResponse } from "../api/types";
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

/** 故障域条目的夹具；默认是「查过的美国故障域」，未查询态由用例显式传 null */
function failureDomain(
  overrides: Partial<SubAuditFailureDomainReport> = {},
): SubAuditFailureDomainReport {
  return {
    domain: "jp.tsdns.top",
    nodeCount: 23,
    usNodeCount: 12,
    entryIps: {
      CHINA_TELECOM: ["34.84.255.241"],
      CHINA_UNICOM: ["43.199.66.164"],
      CHINA_MOBILE: ["43.199.66.164"],
      OVERSEAS: ["43.199.66.164"],
    },
    asns: {
      CHINA_TELECOM: ["AS15169"],
      CHINA_UNICOM: ["AS16509"],
      CHINA_MOBILE: ["AS16509"],
      OVERSEAS: ["AS16509"],
    },
    lineSplit: true,
    ...overrides,
  };
}

/** 取「故障域分布」表里某一行的单元格文本，按表头名定位列，不写死下标 */
function failureDomainCell(columnHeader: string, rowIndex = 0): string {
  const table = document.querySelectorAll("table.admin-table")[0]!;
  const headers = [...table.querySelectorAll("thead th")].map((th) => th.textContent!.trim());
  const index = headers.indexOf(columnHeader);
  expect(
    index,
    `故障域表里没有「${columnHeader}」列，表头是 ${headers.join(" / ")}`,
  ).toBeGreaterThan(-1);
  return table.querySelectorAll("tbody tr")[rowIndex]!.querySelectorAll("td")[index]!.textContent!;
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

  it("故障域表列出各视角的入口 IP 与 ASN——采购标准要卡「入口 ASN ≠ AS16509」，光有域名执行不了", async () => {
    await submitAuditWith({ failureDomains: [failureDomain()] });

    const cell = failureDomainCell("入口 IP / ASN（按视角）");
    expect(cell).toContain("CHINA_TELECOM");
    expect(cell).toContain("34.84.255.241");
    expect(cell).toContain("AS15169");
    expect(cell).toContain("OVERSEAS");
    expect(cell).toContain("43.199.66.164");
    expect(cell).toContain("AS16509");
  });

  it("某视角解析为空时说「解析为空」，不是渲染成空白", async () => {
    await submitAuditWith({
      failureDomains: [
        failureDomain({
          entryIps: { CHINA_TELECOM: [], OVERSEAS: ["43.199.66.164"] },
          asns: { CHINA_TELECOM: [], OVERSEAS: ["AS16509"] },
        }),
      ],
    });

    expect(failureDomainCell("入口 IP / ASN（按视角）")).toContain("解析为空");
  });

  it("ASN 反查不到时说「ASN 未知」，不留空", async () => {
    await submitAuditWith({
      failureDomains: [
        failureDomain({ entryIps: { OVERSEAS: ["43.199.66.164"] }, asns: { OVERSEAS: [] } }),
      ],
    });

    expect(failureDomainCell("入口 IP / ASN（按视角）")).toContain("ASN 未知");
  });

  it("服务端未查询的故障域标「未查询」，不能让人误以为查了但没结果", async () => {
    await submitAuditWith({
      failureDomains: [
        failureDomain({
          domain: "hk.tsdns.top",
          usNodeCount: 0,
          entryIps: null,
          asns: null,
          lineSplit: null,
        }),
      ],
    });

    expect(failureDomainCell("入口 IP / ASN（按视角）")).toContain("未查询入口 IP");
    expect(failureDomainCell("分线路")).toContain("未查询");
    // 未查询绝不能渲染成「否」——那会被读成「查了，没分线路」
    expect(failureDomainCell("分线路")).not.toContain("否");
  });

  it("查过的故障域照常给出分线路结论", async () => {
    await submitAuditWith({ failureDomains: [failureDomain({ lineSplit: false })] });

    expect(failureDomainCell("分线路").trim()).toBe("否");
  });
});
