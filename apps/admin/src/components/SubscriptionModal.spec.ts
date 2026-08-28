import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminSubscriptionResponse, AdminUserResponse, CredentialRevokeResult } from "../api/types";
import { showToast } from "../toast";
import SubscriptionModal from "./SubscriptionModal.vue";

const listSubscriptions = vi.fn<(userId: number) => Promise<AdminSubscriptionResponse[]>>();
const listPlans = vi.fn(async () => []);
const listEnterprises = vi.fn(async () => []);
const credentialRevoke = vi.fn<(subscriptionId: number) => Promise<CredentialRevokeResult>>();

vi.mock("../api", () => ({
  adminApi: () => ({ listSubscriptions, listPlans, listEnterprises, credentialRevoke }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function user(overrides: Partial<AdminUserResponse> = {}): AdminUserResponse {
  return {
    id: 3,
    subject: "sub-3",
    email: "zhang@acme.com",
    role: "MEMBER",
    status: "ACTIVE",
    frontNodeId: null,
    frontNodeName: null,
    landNodeId: null,
    landNodeName: null,
    egressIp: null,
    activeSubscriptions: [],
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
    ...overrides,
  };
}

function subscription(overrides: Partial<AdminSubscriptionResponse> = {}): AdminSubscriptionResponse {
  return {
    id: 7,
    assignmentNo: "7K3M9QX2FT",
    userId: 3,
    enterpriseId: null,
    agentType: "CLAUDE",
    planId: 11,
    name: "Claude 月付",
    planDurationDays: 30,
    planPrice: 99.99,
    planCurrency: "USD",
    startsAt: "2026-08-01T00:00:00Z",
    endsAt: "2026-08-31T00:00:00Z",
    accountEmail: "zhang@acme.com",
    hasCredential: true,
    credentialExpiresAt: "2026-08-31T00:00:00Z",
    credentialStale: false,
    remark: "",
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
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
function queryAll(selector: string): DOMWrapper<Element>[] {
  return Array.from(document.querySelectorAll(selector)).map((el) => new DOMWrapper(el));
}

function buttonByText(text: string): DOMWrapper<Element> {
  const btn = queryAll("button").find((b) => b.text() === text);
  if (!btn) {
    throw new Error(`按钮未找到：${text}`);
  }
  return btn;
}

function buttonExists(text: string): boolean {
  return queryAll("button").some((b) => b.text() === text);
}

async function mountModal(rows: AdminSubscriptionResponse[]) {
  listSubscriptions.mockResolvedValue(rows);
  const wrapper = mount(SubscriptionModal, {
    attachTo: document.body,
    props: { user: user() },
  });
  await vi.waitFor(() => expect(document.querySelectorAll(".sub-item")).toHaveLength(rows.length));
  return wrapper;
}

describe("SubscriptionModal · 吊销凭证", () => {
  it("仅 Claude 且已录入凭证的订阅才显示「吊销凭证」按钮", async () => {
    await mountModal([
      subscription({ id: 1, agentType: "CLAUDE", hasCredential: true }),
      subscription({ id: 2, agentType: "CLAUDE", hasCredential: false }),
      subscription({ id: 3, agentType: "CODEX", hasCredential: true }),
    ]);

    // 三条里只有第一条（Claude + 有凭证）该出现按钮
    expect(queryAll("button").filter((b) => b.text() === "吊销凭证")).toHaveLength(1);
  });

  it("点击后需二次确认，确认后才调用吊销接口", async () => {
    const row = subscription();
    await mountModal([row]);
    credentialRevoke.mockResolvedValue({ upstreamRevoked: true });

    await buttonByText("吊销凭证").trigger("click");
    // 未确认前不能调用接口
    expect(credentialRevoke).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain("确认吊销订阅");
    expect(document.body.textContent).toContain(row.name);

    listSubscriptions.mockResolvedValue([{ ...row, hasCredential: false, credentialExpiresAt: null }]);
    await buttonByText("吊销").trigger("click");

    await vi.waitFor(() => expect(credentialRevoke).toHaveBeenCalledWith(row.id));
    // 吊销与查询列表都发生了，列表重新拉取以反映凭据状态变化
    await vi.waitFor(() => expect(listSubscriptions).toHaveBeenCalledTimes(2));
  });

  it("upstreamRevoked 为 true：提示明确说「已吊销」，不留常驻告警", async () => {
    const row = subscription();
    await mountModal([row]);
    credentialRevoke.mockResolvedValue({ upstreamRevoked: true });
    listSubscriptions.mockResolvedValue([{ ...row, hasCredential: false }]);

    await buttonByText("吊销凭证").trigger("click");
    await buttonByText("吊销").trigger("click");

    await vi.waitFor(() => expect(showToast).toHaveBeenCalledWith("success", "凭证已吊销"));
    expect(document.body.textContent).not.toContain("上游");
  });

  it("upstreamRevoked 为 false：不能报笼统的成功，必须提示上游可能仍然有效", async () => {
    const row = subscription();
    await mountModal([row]);
    credentialRevoke.mockResolvedValue({ upstreamRevoked: false });
    listSubscriptions.mockResolvedValue([{ ...row, hasCredential: false }]);

    await buttonByText("吊销凭证").trigger("click");
    await buttonByText("吊销").trigger("click");

    await vi.waitFor(() => expect(credentialRevoke).toHaveBeenCalledWith(row.id));

    // 关键断言：绝不能笼统报「已吊销」这类完全成功的说法
    expect(showToast).not.toHaveBeenCalledWith("success", "凭证已吊销");
    expect(showToast).not.toHaveBeenCalledWith(
      "success",
      expect.stringContaining("已吊销"),
    );
    // 必须出现「上游可能仍然有效」这类明确提示，而不是笼统的成功提示
    await vi.waitFor(() => expect(document.body.textContent).toContain("上游"));
    expect(document.body.textContent).toContain("可能仍然有效");
    expect(buttonExists("知道了")).toBe(true);
  });
});
