import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminSubscriptionResponse, AdminUserResponse, CredentialRevokeResult } from "../api/types";
import { showToast } from "../toast";
import { formatAssignmentNo } from "../utils/format";
import UserSubscriptionsView from "./UserSubscriptionsView.vue";

const getUser = vi.fn<(id: number) => Promise<AdminUserResponse>>();
const listSubscriptions = vi.fn<(userId: number) => Promise<AdminSubscriptionResponse[]>>();
const listPlans = vi.fn(async () => []);
const listEnterprises = vi.fn(async () => []);
const credentialRevoke = vi.fn<(subscriptionId: number) => Promise<CredentialRevokeResult>>();

vi.mock("../api", () => ({
  adminApi: () => ({ getUser, listSubscriptions, listPlans, listEnterprises, credentialRevoke }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));
// 页面从路由参数取用户 id；这里只测页面自身逻辑，不架真路由
vi.mock("vue-router", () => ({ useRoute: () => ({ params: { id: "3" } }) }));

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
  getUser.mockResolvedValue(user());
});

afterEach(() => {
  // ConfirmDialog / CredentialIssueModal 用 Teleport 挂到 body，测试间要清掉，
  // 不然下一次挂载撞上上一次残留的 DOM
  document.body.innerHTML = "";
});

/** 弹窗用 Teleport 挂到 body，游离节点里的内容 wrapper.find 够不着，须从 document 上取 */
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

/** 常驻警示块自己的文案，与列表里其它订阅的展示字段（如分配号）互不相干，断言时不要混到一起 */
function warningText(): string | null {
  return document.querySelector(".revoke-warn p")?.textContent ?? null;
}

async function mountView(rows: AdminSubscriptionResponse[]) {
  listSubscriptions.mockResolvedValue(rows);
  const wrapper = mount(UserSubscriptionsView, {
    attachTo: document.body,
    // 返回链接是真路由的事，这里桩掉即可
    global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
  });
  await vi.waitFor(() => expect(document.querySelectorAll(".sub-item")).toHaveLength(rows.length));
  return wrapper;
}

describe("UserSubscriptionsView · 页面骨架", () => {
  it("按路由里的用户 id 拉取用户，标题带上邮箱", async () => {
    await mountView([subscription()]);

    expect(getUser).toHaveBeenCalledWith(3);
    await vi.waitFor(() => expect(document.body.textContent).toContain("订阅管理：zhang@acme.com"));
  });

  it("用户拉取失败时整页降级为错误提示，不再露出分配入口", async () => {
    getUser.mockRejectedValue(new Error("用户不存在"));
    listSubscriptions.mockResolvedValue([]);
    mount(UserSubscriptionsView, {
      attachTo: document.body,
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });

    await vi.waitFor(() => expect(document.body.textContent).toContain("用户不存在"));
    expect(buttonExists("分配订阅")).toBe(false);
  });
});

describe("UserSubscriptionsView · 吊销凭证", () => {
  it("仅 Claude 且已录入凭证的订阅才显示「吊销凭证」按钮", async () => {
    await mountView([
      subscription({ id: 1, agentType: "CLAUDE", hasCredential: true }),
      subscription({ id: 2, agentType: "CLAUDE", hasCredential: false }),
      subscription({ id: 3, agentType: "CODEX", hasCredential: true }),
    ]);

    // 三条里只有第一条（Claude + 有凭证）该出现按钮
    expect(queryAll("button").filter((b) => b.text() === "吊销凭证")).toHaveLength(1);
  });

  it("点击后需二次确认，确认后才调用吊销接口", async () => {
    const row = subscription();
    await mountView([row]);
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
    await mountView([row]);
    credentialRevoke.mockResolvedValue({ upstreamRevoked: true });
    listSubscriptions.mockResolvedValue([{ ...row, hasCredential: false }]);

    await buttonByText("吊销凭证").trigger("click");
    await buttonByText("吊销").trigger("click");

    await vi.waitFor(() => expect(showToast).toHaveBeenCalledWith("success", "凭证已吊销"));
    expect(document.body.textContent).not.toContain("上游");
  });

  it("upstreamRevoked 为 false：不能报笼统的成功，必须提示上游可能仍然有效，且带上该订阅的标识", async () => {
    const row = subscription();
    await mountView([row]);
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
    await vi.waitFor(() => expect(warningText()).toContain("上游"));
    expect(warningText()).toContain("可能仍然有效");
    // 关键断言：警示文案必须带上这条订阅的标识（订阅名 + 分配号），
    // 否则同一用户下多条 Claude 订阅时，管理员无法判断这条常驻提示到底是关于哪条订阅的
    expect(warningText()).toContain(row.name);
    expect(warningText()).toContain(formatAssignmentNo(row.assignmentNo));
    expect(buttonExists("知道了")).toBe(true);
  });

  it("归因不会串：吊销 A 拿到常驻警示后，对 B 做别的操作，警示仍标注的是 A 而不是 B", async () => {
    const rowA = subscription({ id: 1, name: "Claude 月付 A", assignmentNo: "AAAAABBBBB" });
    const rowB = subscription({ id: 2, name: "Claude 月付 B", assignmentNo: "CCCCCDDDDD" });
    await mountView([rowA, rowB]);
    credentialRevoke.mockResolvedValue({ upstreamRevoked: false });
    listSubscriptions.mockResolvedValue([{ ...rowA, hasCredential: false }, rowB]);

    // 对 A 吊销，拿到常驻警示
    const revokeButtons = queryAll("button").filter((b) => b.text() === "吊销凭证");
    expect(revokeButtons).toHaveLength(2);
    await revokeButtons[0].trigger("click");
    await buttonByText("吊销").trigger("click");
    await vi.waitFor(() => expect(credentialRevoke).toHaveBeenCalledWith(rowA.id));
    await vi.waitFor(() => expect(warningText()).toContain(rowA.name));
    expect(warningText()).toContain(formatAssignmentNo(rowA.assignmentNo));
    expect(warningText()).not.toContain(rowB.name);

    // 未关闭警示，转去编辑 B（切到表单视图再切回来）——警示必须仍挂着、且仍标注 A，不能被当成 B 的结果
    const editButtons = queryAll("button").filter((b) => b.text() === "编辑");
    expect(editButtons).toHaveLength(2);
    await editButtons[1].trigger("click"); // 列表第二条是 B（reload 后 [A, B] 顺序不变）
    expect(warningText()).toContain(rowA.name);
    expect(warningText()).not.toContain(rowB.name);
    await buttonByText("取消").trigger("click");
    expect(warningText()).toContain(rowA.name);
    expect(warningText()).toContain(formatAssignmentNo(rowA.assignmentNo));
    expect(warningText()).not.toContain(formatAssignmentNo(rowB.assignmentNo));
  });
});
