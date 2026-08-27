import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import type { AdminSubscriptionResponse, CredentialAuthorizationStart, CredentialIssueResult } from "../api/types";
import { formatDateTime } from "../utils/format";
import { showToast } from "../toast";
import CredentialIssueModal from "./CredentialIssueModal.vue";

const credentialAuthorizeUrl = vi.fn<(subscriptionId: number) => Promise<CredentialAuthorizationStart>>();
const credentialExchange = vi.fn<(subscriptionId: number, body: unknown) => Promise<CredentialIssueResult>>();

vi.mock("../api", () => ({
  adminApi: () => ({ credentialAuthorizeUrl, credentialExchange }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

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
    hasCredential: false,
    credentialExpiresAt: null,
    credentialStale: false,
    remark: "",
    createdAt: "2026-08-01T00:00:00Z",
    updatedAt: "2026-08-01T00:00:00Z",
    ...overrides,
  };
}

const authStart: CredentialAuthorizationStart = {
  authUrl: "https://anthropic.example.com/oauth/authorize?client_id=abc&state=sess-1",
  sessionId: "sess-1",
  accountEmail: "zhang@acme.com",
  egressIp: "203.0.113.7",
};

const issueResult: CredentialIssueResult = {
  accountEmail: "zhang@acme.com",
  grantedScope: "user:profile",
  expiresAt: "2026-08-31T00:00:00Z",
};

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

function mountModal(row: AdminSubscriptionResponse = subscription()) {
  return mount(CredentialIssueModal, {
    attachTo: document.body,
    props: { subscription: row },
  });
}

/** 各步骤主操作按钮：admin-btn（取消/关闭是 admin-btn-ghost，不会撞上） */
function primaryButton(): DOMWrapper<Element> {
  return query("button.admin-btn");
}

describe("CredentialIssueModal", () => {
  it("三步状态流转：发起 → 授权 → 完成", async () => {
    credentialAuthorizeUrl.mockResolvedValue(authStart);
    credentialExchange.mockResolvedValue(issueResult);
    const wrapper = mountModal();

    // ① 发起
    expect(document.querySelector("#issue-auth-url")).toBeNull();
    await primaryButton().trigger("click");
    expect(credentialAuthorizeUrl).toHaveBeenCalledWith(7);

    // ② 授权：授权信息与出口 IP 都已展示，输入授权码后兑换
    await vi.waitFor(() => expect(document.querySelector("#issue-auth-url")).not.toBeNull());
    expect((query("#issue-auth-url").element as HTMLInputElement).value).toBe(authStart.authUrl);
    expect((query("#issue-egress-ip").element as HTMLInputElement).value).toBe(authStart.egressIp);
    expect(document.body.textContent).toContain("zhang@acme.com");

    await query("#issue-code").setValue("auth-code-1");
    await primaryButton().trigger("click");
    expect(credentialExchange).toHaveBeenCalledWith(7, { sessionId: "sess-1", code: "auth-code-1" });

    // ③ 完成：展示兑换结果
    await vi.waitFor(() => expect(document.body.textContent).toContain("user:profile"));
    expect(document.body.textContent).toContain(formatDateTime(issueResult.expiresAt));
    // 完成态不再渲染授权码/授权链接，sessionId 与授权码没有任何残留展示
    expect(document.querySelector("#issue-auth-url")).toBeNull();
    expect(document.querySelector("#issue-code")).toBeNull();

    // 关闭走「issued」而不是「close」，让父组件刷新列表
    await query("button.admin-btn-ghost").trigger("click");
    expect(wrapper.emitted("issued")).toBeTruthy();
    expect(wrapper.emitted("close")).toBeFalsy();
  });

  it("发起签发失败：停在发起步，报错可见，且能重试", async () => {
    credentialAuthorizeUrl
      .mockRejectedValueOnce(new BizError(999999, "网络异常"))
      .mockResolvedValueOnce(authStart);
    mountModal();

    await primaryButton().trigger("click");
    await vi.waitFor(() => expect(showToast).toHaveBeenCalledWith("error", "网络异常"));
    // 没有推进到授权步，按钮也没有卡在「生成中…」
    expect(document.querySelector("#issue-auth-url")).toBeNull();
    expect(primaryButton().text()).toBe("生成授权链接");

    // 原样重试即可成功，不需要任何额外操作
    await primaryButton().trigger("click");
    await vi.waitFor(() => expect(document.querySelector("#issue-auth-url")).not.toBeNull());
    expect(credentialAuthorizeUrl).toHaveBeenCalledTimes(2);
  });

  it("兑换失败：停在授权步、授权码保留，重试沿用同一份 sessionId", async () => {
    credentialAuthorizeUrl.mockResolvedValue(authStart);
    credentialExchange
      .mockRejectedValueOnce(new BizError(410038, "授权码已过期"))
      .mockResolvedValueOnce(issueResult);
    mountModal();

    await primaryButton().trigger("click");
    await vi.waitFor(() => expect(document.querySelector("#issue-auth-url")).not.toBeNull());

    await query("#issue-code").setValue("auth-code-1");
    await primaryButton().trigger("click");
    await vi.waitFor(() => expect(showToast).toHaveBeenCalledWith("error", "授权码已过期"));

    // 仍停在授权步，授权码没被清空，不必重新点「生成授权链接」
    expect(document.querySelector("#issue-auth-url")).not.toBeNull();
    expect((query("#issue-code").element as HTMLInputElement).value).toBe("auth-code-1");
    expect(credentialAuthorizeUrl).toHaveBeenCalledTimes(1);

    // 直接重试兑换即可成功
    await primaryButton().trigger("click");
    await vi.waitFor(() => expect(document.body.textContent).toContain("user:profile"));
    expect(credentialExchange).toHaveBeenNthCalledWith(2, 7, { sessionId: "sess-1", code: "auth-code-1" });
  });

  it("订阅未录入账号邮箱时，授权步显示需自行确认的提醒文案", async () => {
    credentialAuthorizeUrl.mockResolvedValue({ ...authStart, accountEmail: null });
    mountModal(subscription({ accountEmail: null }));

    await primaryButton().trigger("click");
    await vi.waitFor(() => expect(document.querySelector("#issue-auth-url")).not.toBeNull());

    expect(document.body.textContent).toContain("该订阅未录入账号邮箱，请自行确认要授权的是哪个账号");
  });
});
