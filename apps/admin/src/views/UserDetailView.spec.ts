import { DOMWrapper, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
  AdminNodeResponse,
  AdminSubscriptionResponse,
  AdminUserResponse,
  CredentialRevokeResult,
  UserSaveRequest,
} from "../api/types";
import { useRebindStore } from "../stores/rebind";
import { showToast } from "../toast";
import { formatAssignmentNo } from "../utils/format";
import UserDetailView from "./UserDetailView.vue";

const getUser = vi.fn<(id: number) => Promise<AdminUserResponse>>();
const listNodes = vi.fn<() => Promise<AdminNodeResponse[]>>(async () => []);
const updateUser = vi.fn<(id: number, body: UserSaveRequest) => Promise<void>>(
  async () => undefined,
);
const listSubscriptions = vi.fn<(userId: number) => Promise<AdminSubscriptionResponse[]>>();
const listPlans = vi.fn(async () => []);
const listEnterprises = vi.fn(async () => []);
const credentialRevoke = vi.fn<(subscriptionId: number) => Promise<CredentialRevokeResult>>();
const unbindSubscriptionDevice = vi.fn<(id: number) => Promise<void>>(async () => undefined);
// 角标 store 用它数待处理换机申请；store 只取 length，故这里给几个空对象就够
const listDeviceRebindRequests = vi.fn<(status?: string) => Promise<unknown[]>>(async () => []);

vi.mock("../api", () => ({
  adminApi: () => ({
    getUser,
    listNodes,
    updateUser,
    listSubscriptions,
    listPlans,
    listEnterprises,
    credentialRevoke,
    unbindSubscriptionDevice,
    listDeviceRebindRequests,
  }),
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
    remark: null,
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
    ...overrides,
  };
}

function node(overrides: Partial<AdminNodeResponse> = {}): AdminNodeResponse {
  return {
    id: 1,
    name: "US-01",
    role: "FRONT",
    protocol: "TROJAN",
    serverAddr: "us.example.com",
    port: 443,
    extraConfig: {},
    egressIp: null,
    egressTimezone: null,
    status: "ENABLED",
    remark: null,
    secretConfigured: true,
    capacity: null,
    assignedUserCount: null,
    groupId: null,
    groupName: null,
    sourceType: null,
    failureDomain: null,
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
    ...overrides,
  };
}

function subscription(
  overrides: Partial<AdminSubscriptionResponse> = {},
): AdminSubscriptionResponse {
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
    extraUsageDisabled: false,
    boundDevice: null,
    remark: "",
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  // 这页会推一把导航轨的角标 store（解绑会作废该订阅的待处理换机申请）
  setActivePinia(createPinia());
  vi.clearAllMocks();
  getUser.mockResolvedValue(user());
  // jsdom 不实现 scrollIntoView，AdminSelect 展开面板定位高亮项时会调它
  Element.prototype.scrollIntoView = vi.fn();
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

/** 某张卡片内、文案为 text 的按钮。页面上「保存」不止一个，必须按卡片圈定 */
function buttonInCard(cardSelector: string, text: string): DOMWrapper<Element> {
  const btn = Array.from(document.querySelectorAll(`${cardSelector} button`))
    .map((el) => new DOMWrapper(el))
    .find((b) => b.text() === text);
  if (!btn) {
    throw new Error(`${cardSelector} 里没有「${text}」按钮`);
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
  const wrapper = mount(UserDetailView, {
    attachTo: document.body,
    // 返回链接是真路由的事，这里桩掉即可
    global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
  });
  await vi.waitFor(() => expect(document.querySelectorAll(".sub-item")).toHaveLength(rows.length));
  return wrapper;
}

describe("UserDetailView · 页面骨架", () => {
  it("按路由里的用户 id 拉取用户，页头是身份卡：邮箱做主标题，带状态与身份事实", async () => {
    await mountView([subscription()]);

    expect(getUser).toHaveBeenCalledWith(3);
    await vi.waitFor(() =>
      expect(document.querySelector(".user-head-email")?.textContent).toContain("zhang@acme.com"),
    );
    expect(document.querySelector(".user-head")?.textContent).toContain("用户管理");
    // 身份事实：状态徽标 + Logto id
    expect(document.querySelector(".user-head .state")?.textContent).toContain("正常");
    expect(document.querySelector(".user-head")?.textContent).toContain("sub-3");
  });

  // 组织没开 usage credits 时凭证本身有效、只是 Fable 用不了，必须显式说出来——
  // 否则只能等用户来报「Fable 又不见了」才发现，而那要靠逐层排查才定位得到
  it("组织未开 usage credits 时标注「Fable 不可用」，并给出具体的开启路径", async () => {
    await mountView([subscription({ extraUsageDisabled: true })]);

    const item = document.querySelector(".sub-item")!;
    expect(item.textContent).toContain("Fable 不可用");
    expect(item.textContent).toContain("usage credits");
    expect(item.textContent).toContain("Admin settings");
  });

  it("未探测到该状态时不报警：旧式/手工凭证无从得知，不能当成「知道它关着」", async () => {
    await mountView([subscription({ extraUsageDisabled: false })]);

    expect(document.querySelector(".sub-item")?.textContent).not.toContain("Fable 不可用");
  });

  it("用户拉取失败时整页降级为错误提示，不再露出分配入口", async () => {
    getUser.mockRejectedValue(new Error("用户不存在"));
    listSubscriptions.mockResolvedValue([]);
    mount(UserDetailView, {
      attachTo: document.body,
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });

    await vi.waitFor(() => expect(document.body.textContent).toContain("用户不存在"));
    expect(buttonExists("分配订阅")).toBe(false);
  });
});

describe("UserDetailView · 吊销凭证", () => {
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

    listSubscriptions.mockResolvedValue([
      { ...row, hasCredential: false, credentialExpiresAt: null },
    ]);
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
    expect(showToast).not.toHaveBeenCalledWith("success", expect.stringContaining("已吊销"));
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

describe("UserDetailView · 设备绑定", () => {
  it("未绑定时如实说未绑定，且不给解绑入口", async () => {
    await mountView([subscription({ id: 5, boundDevice: null })]);

    expect(document.querySelector(".sub-item")?.textContent).toContain("未绑定设备");
    expect(buttonExists("解绑设备")).toBe(false);
  });

  it("已绑定时把设备三要素与绑定时刻都摆出来", async () => {
    await mountView([
      subscription({
        id: 5,
        boundDevice: {
          name: "月白的 MacBook",
          os: "macos 26.6",
          model: "Mac17,9",
          boundAt: "2026-09-10T02:00:00Z",
          lastSeenAt: "2026-09-15T09:00:00Z",
        },
      }),
    ]);
    const text = document.querySelector(".sub-item")?.textContent ?? "";

    expect(text).toContain("月白的 MacBook");
    expect(text).toContain("macos 26.6");
    expect(text).toContain("Mac17,9");
  });

  it("已绑定时同时给出最近活跃时刻：绑了半年却一个月没动过，和昨天刚绑的不是一回事", async () => {
    // 相对说法是按「现在」算的，钉住它才能断言
    vi.spyOn(Date, "now").mockReturnValue(new Date("2026-09-15T12:00:00Z").getTime());
    await mountView([
      subscription({
        id: 5,
        boundDevice: {
          name: "月白的 MacBook",
          os: "macos 26.6",
          model: "Mac17,9",
          boundAt: "2026-03-10T02:00:00Z",
          lastSeenAt: "2026-09-15T09:00:00Z",
        },
      }),
    ]);
    const text = document.querySelector(".sub-item")?.textContent ?? "";

    expect(text).toContain("最近活跃");
    expect(text).toContain("3 小时前");
  });

  it("解绑要先过确认框，确认后调接口并重拉订阅，且确认文案提示会作废待处理换机申请", async () => {
    const row = subscription({
      id: 5,
      name: "Claude 月付",
      assignmentNo: "7K3M9QX2FT",
      boundDevice: {
        name: "月白的 MacBook",
        os: "macos 26.6",
        model: "Mac17,9",
        boundAt: "2026-09-10T02:00:00Z",
        lastSeenAt: "2026-09-15T09:00:00Z",
      },
    });
    await mountView([row]);

    await buttonByText("解绑设备").trigger("click");
    // 未确认前不能调用接口
    expect(unbindSubscriptionDevice).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain("月白的 MacBook");
    expect(document.body.textContent).toContain(formatAssignmentNo(row.assignmentNo));
    // 解绑会连带作废该订阅挂着的待处理换机申请，确认文案要把这个副作用说清楚
    expect(document.body.textContent).toContain("换机申请");

    listSubscriptions.mockResolvedValue([{ ...row, boundDevice: null }]);
    await buttonByText("解绑").trigger("click");

    await vi.waitFor(() => expect(unbindSubscriptionDevice).toHaveBeenCalledWith(row.id));
    expect(showToast).toHaveBeenCalledWith("success", "已解绑，该订阅可在任意设备上重新绑定");
    // 解绑与查询列表都发生了，列表重新拉取以反映绑定状态变化
    await vi.waitFor(() => expect(listSubscriptions).toHaveBeenCalledTimes(2));
  });

  // 服务端解绑会把该订阅的待处理换机申请一并作废，角标却是另一份数据：
  // 不推这一把，导航轨就会一直挂着一条已经不存在的待办，只有整页刷新才消得掉
  it("解绑成功后刷新导航轨的换机申请角标", async () => {
    const row = subscription({
      id: 5,
      boundDevice: {
        name: "月白的 MacBook",
        os: "macos 26.6",
        model: "Mac17,9",
        boundAt: "2026-09-10T02:00:00Z",
        lastSeenAt: "2026-09-15T09:00:00Z",
      },
    });
    await mountView([row]);
    const store = useRebindStore();
    // 解绑前角标还没被这页动过
    expect(store.pendingCount).toBe(0);

    listDeviceRebindRequests.mockResolvedValue([{}, {}]);
    await buttonByText("解绑设备").trigger("click");
    await buttonByText("解绑").trigger("click");

    await vi.waitFor(() => expect(store.pendingCount).toBe(2));
    expect(listDeviceRebindRequests).toHaveBeenCalledWith("PENDING");
  });

  it("解绑失败时不去动角标——服务端什么都没作废", async () => {
    const row = subscription({
      id: 5,
      boundDevice: {
        name: "月白的 MacBook",
        os: "macos 26.6",
        model: "Mac17,9",
        boundAt: "2026-09-10T02:00:00Z",
        lastSeenAt: "2026-09-15T09:00:00Z",
      },
    });
    await mountView([row]);
    unbindSubscriptionDevice.mockRejectedValueOnce(new Error("Failed to fetch"));

    await buttonByText("解绑设备").trigger("click");
    await buttonByText("解绑").trigger("click");

    await vi.waitFor(() => expect(showToast).toHaveBeenCalled());
    expect(listDeviceRebindRequests).not.toHaveBeenCalled();
  });

  // 「可以在任意设备上重新绑定」听着像无害的整理，实则原设备当场就没席位了
  it("解绑确认框把「原设备当场失去席位」这个代价说出来", async () => {
    await mountView([
      subscription({
        id: 5,
        boundDevice: {
          name: "月白的 MacBook",
          os: "macos 26.6",
          model: "Mac17,9",
          boundAt: "2026-09-10T02:00:00Z",
          lastSeenAt: "2026-09-15T09:00:00Z",
        },
      }),
    ]);

    await buttonByText("解绑设备").trigger("click");
    const message = document.body.textContent ?? "";

    expect(message).toContain("当场失去这个席位");
    expect(message).toContain("重新确认绑定");
  });

  // 桌面端读不到硬件型号时机型就是空串，无条件拼「系统 · 机型」会留下一个吊着的分隔符
  it("机型为空时不留下吊着的分隔符", async () => {
    await mountView([
      subscription({
        id: 5,
        boundDevice: {
          name: "DESKTOP-4F2",
          os: "windows 11",
          model: "",
          boundAt: "2026-09-10T02:00:00Z",
          lastSeenAt: "2026-09-15T09:00:00Z",
        },
      }),
    ]);
    const text = document.querySelector(".sub-item")?.textContent ?? "";

    expect(text).toContain("DESKTOP-4F2（windows 11）");
    expect(text).not.toContain("windows 11 · ");
  });
});

describe("UserDetailView · 链路资源", () => {
  /** AdminSelect 的触发按钮按 aria-label 找；面板 Teleport 到 body，选项从 document 上取 */
  function selectTrigger(label: string): DOMWrapper<Element> {
    const btn = document.querySelector(`button[aria-label="${label}"]`);
    if (!btn) {
      throw new Error(`下拉未找到：${label}`);
    }
    return new DOMWrapper(btn);
  }

  it("没有改动时保存按钮禁用——没有可保存的东西", async () => {
    getUser.mockResolvedValue(user({ frontNodeId: 1, landNodeId: 11 }));
    listNodes.mockResolvedValue([
      node({ id: 1, name: "US-01", role: "FRONT" }),
      node({
        id: 11,
        name: "LAND-东京",
        role: "LAND",
        capacity: 10,
        assignedUserCount: 3,
        egressIp: "1.2.3.4",
      }),
    ]);
    await mountView([]);
    // 等两个下拉都回显出当前分配，说明用户与节点都已加载完
    await vi.waitFor(() => expect(document.body.textContent).toContain("US-01"));
    await vi.waitFor(() => expect(document.body.textContent).toContain("LAND-东京"));

    expect(buttonInCard(".link-card", "保存").attributes("disabled")).toBeDefined();
  });

  it("换落地节点后保存提交新节点 id，用户处置态原样透传——这页不动状态，状态在用户列表切换", async () => {
    getUser.mockResolvedValue(user({ status: "SUSPENDED", frontNodeId: 1, landNodeId: 11 }));
    listNodes.mockResolvedValue([
      node({ id: 1, name: "US-01", role: "FRONT" }),
      node({ id: 11, name: "LAND-东京", role: "LAND", capacity: 10, assignedUserCount: 3 }),
      node({ id: 12, name: "LAND-新宿", role: "LAND", capacity: 10, assignedUserCount: 0 }),
    ]);
    await mountView([]);
    await vi.waitFor(() => expect(document.body.textContent).toContain("LAND-东京"));

    await selectTrigger("落地节点").trigger("click");
    const option = queryAll("li").find((li) => li.text().includes("LAND-新宿"));
    if (!option) {
      throw new Error("选项未找到：LAND-新宿");
    }
    await option.trigger("click");
    await buttonInCard(".link-card", "保存").trigger("click");

    await vi.waitFor(() =>
      expect(updateUser).toHaveBeenCalledWith(3, {
        status: "SUSPENDED",
        frontNodeId: 1,
        landNodeId: 12,
        remark: "",
      }),
    );
  });

  it("改链路时备注原样带回，不会把管理员写的备注顺手清掉", async () => {
    getUser.mockResolvedValue(user({ frontNodeId: 1, landNodeId: 11, remark: "老客户" }));
    listNodes.mockResolvedValue([
      node({ id: 1, name: "US-01", role: "FRONT" }),
      node({ id: 11, name: "LAND-东京", role: "LAND", capacity: 10, assignedUserCount: 3 }),
      node({ id: 12, name: "LAND-新宿", role: "LAND", capacity: 10, assignedUserCount: 0 }),
    ]);
    await mountView([]);
    await vi.waitFor(() => expect(document.body.textContent).toContain("LAND-东京"));

    await selectTrigger("落地节点").trigger("click");
    const option = queryAll("li").find((li) => li.text().includes("LAND-新宿"));
    if (!option) {
      throw new Error("选项未找到：LAND-新宿");
    }
    await option.trigger("click");
    await buttonInCard(".link-card", "保存").trigger("click");

    await vi.waitFor(() =>
      expect(updateUser).toHaveBeenCalledWith(3, expect.objectContaining({ remark: "老客户" })),
    );
  });
});

describe("UserDetailView · 备注", () => {
  function remarkInput(): DOMWrapper<HTMLInputElement> {
    const input = document.querySelector<HTMLInputElement>("#user-remark");
    if (!input) {
      throw new Error("备注输入框未找到");
    }
    return new DOMWrapper(input);
  }

  it("回显服务端已有的备注", async () => {
    getUser.mockResolvedValue(user({ remark: "老客户，续费谈过" }));
    await mountView([]);
    await vi.waitFor(() => expect(remarkInput().element.value).toBe("老客户，续费谈过"));
  });

  it("没写过备注时输入框是空的，不是「null」这四个字", async () => {
    getUser.mockResolvedValue(user({ remark: null }));
    await mountView([]);
    await vi.waitFor(() => expect(remarkInput().element.value).toBe(""));
  });

  it("没有改动时保存按钮禁用——没有可保存的东西", async () => {
    getUser.mockResolvedValue(user({ remark: "老客户" }));
    await mountView([]);
    await vi.waitFor(() => expect(remarkInput().element.value).toBe("老客户"));

    expect(buttonInCard(".remark-card", "保存").attributes("disabled")).toBeDefined();
  });

  it("输入框硬卡在 50 字——备注是一眼读完的一句话，长过这个尺度不该往这里塞", async () => {
    getUser.mockResolvedValue(user());
    await mountView([]);
    await vi.waitFor(() => expect(document.querySelector("#user-remark")).not.toBeNull());

    expect(remarkInput().attributes("maxlength")).toBe("50");
  });

  it("给出字数提示：maxlength 是硬截断，没有反馈会让人以为键盘坏了", async () => {
    getUser.mockResolvedValue(user({ remark: "老客户" }));
    await mountView([]);
    await vi.waitFor(() => expect(remarkInput().element.value).toBe("老客户"));

    const counter = document.querySelector(".remark-card .remark-count");
    expect(counter?.textContent).toContain("3 / 50");

    await remarkInput().setValue("老客户，续费谈过");
    expect(document.querySelector(".remark-card .remark-count")?.textContent).toContain("8 / 50");
  });

  it("改了备注后保存，处置态与链路分配原样带回", async () => {
    getUser.mockResolvedValue(user({ status: "SUSPENDED", frontNodeId: 1, landNodeId: 11 }));
    listNodes.mockResolvedValue([
      node({ id: 1, name: "US-01", role: "FRONT" }),
      node({ id: 11, name: "LAND-东京", role: "LAND", capacity: 10, assignedUserCount: 3 }),
    ]);
    await mountView([]);
    await vi.waitFor(() => expect(document.body.textContent).toContain("LAND-东京"));

    await remarkInput().setValue("试用期，月底回访");
    await buttonInCard(".remark-card", "保存").trigger("click");

    await vi.waitFor(() =>
      expect(updateUser).toHaveBeenCalledWith(3, {
        status: "SUSPENDED",
        frontNodeId: 1,
        landNodeId: 11,
        remark: "试用期，月底回访",
      }),
    );
    expect(showToast).toHaveBeenCalledWith("success", "已保存");
  });

  it("清空备注也算改动，能提交出去", async () => {
    getUser.mockResolvedValue(user({ remark: "写错了" }));
    await mountView([]);
    await vi.waitFor(() => expect(remarkInput().element.value).toBe("写错了"));

    await remarkInput().setValue("");
    await buttonInCard(".remark-card", "保存").trigger("click");

    await vi.waitFor(() =>
      expect(updateUser).toHaveBeenCalledWith(3, expect.objectContaining({ remark: "" })),
    );
  });

  it("保存失败时把服务端说法原样提示出来", async () => {
    getUser.mockResolvedValue(user({ remark: "" }));
    updateUser.mockRejectedValueOnce(new Error("timeout"));
    await mountView([]);
    await vi.waitFor(() => expect(document.querySelector("#user-remark")).not.toBeNull());

    await remarkInput().setValue("随手记");
    await buttonInCard(".remark-card", "保存").trigger("click");

    await vi.waitFor(() =>
      expect(showToast).toHaveBeenCalledWith("error", "保存备注失败：timeout"),
    );
  });
});

describe("UserDetailView · 待开通订阅", () => {
  it("起止为 null 的订阅显示「待开通」徽标，起止列为占位符，签发按钮禁用并说明先填起期", async () => {
    await mountView([
      subscription({
        startsAt: null,
        endsAt: null,
        remark: "自助购买，订单 LN20260915083005123456",
      }),
    ]);

    const item = document.querySelector(".sub-item")!;
    expect(item.querySelector(".pill.pending")?.textContent).toBe("待开通");
    const facts = Array.from(item.querySelectorAll(".sub-fact dd")).map((dd) => dd.textContent);
    expect(facts[0]).toBe("—");
    expect(facts[1]).toBe("—");
    const issue = buttonByText("签发凭证");
    expect(issue.attributes("disabled")).toBeDefined();
    expect(issue.attributes("title")).toBe("先填写起期开通后再签发");
  });

  it("已填起期的订阅不显示「待开通」，签发按钮可用", async () => {
    await mountView([subscription()]);

    expect(document.querySelector(".pill.pending")).toBeNull();
    const issue = buttonByText("签发凭证");
    expect(issue.attributes("disabled")).toBeUndefined();
  });
});
