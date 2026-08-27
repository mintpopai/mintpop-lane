import { describe, expect, it } from "vitest";
import type { AdminSubscriptionResponse, EnterpriseResponse, PlanResponse } from "../api/types";
import {
  agentTypeOptions,
  enterpriseOptionsForAgent,
  buildSubscriptionCreatePayload,
  buildSubscriptionUpdatePayload,
  computeEndsAt,
  emptySubscriptionForm,
  formatPlanLabel,
  planOptionsForAgent,
  subscriptionToForm,
  validateSubscriptionForm,
} from "./subscriptionForm";

const sample: AdminSubscriptionResponse = {
  id: 3,
  assignmentNo: "0f8fad5bd9cb469fa16570867728950e",
  userId: 7,
  enterpriseId: null,
  agentType: "CLAUDE",
  planId: 11,
  name: "Claude 月付",
  planDurationDays: 30,
  planPrice: 99.99,
  planCurrency: "USD",
  startsAt: "2026-08-01T00:00:00Z",
  endsAt: "2026-08-31T00:00:00Z",
  accountEmail: null,
  hasCredential: true,
  credentialExpiresAt: "2026-08-31T00:00:00Z",
  credentialStale: false,
  remark: "线下收款",
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

const plan: PlanResponse = {
  id: 11,
  name: "Claude 月付",
  agentType: "CLAUDE",
  durationDays: 30,
  price: 99.99,
  currency: "USD",
  enabled: true,
  remark: null,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

const enterprise: EnterpriseResponse = {
  id: 21,
  name: "Acme 科技",
  domain: "acme.com",
  agentTypes: ["CLAUDE"],
  enabled: true,
  remark: null,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

describe("subscriptionForm", () => {
  it("空表单未选 agent 类型与套餐、起期取当下", () => {
    const now = new Date("2026-08-20T12:00:00Z");
    const form = emptySubscriptionForm(now);
    expect(form.id).toBeNull();
    expect(form.agentType).toBeNull();
    expect(form.planId).toBeNull();
    expect(form.startsAt).toEqual(now);
    expect(form.credential).toBe("");
  });

  it("回填把 UTC 串解析成 Date，带上 agent 类型与套餐 id，不含凭据", () => {
    const form = subscriptionToForm(sample);
    expect(form.agentType).toBe("CLAUDE");
    expect(form.planId).toBe(11);
    expect(form.startsAt?.toISOString()).toBe("2026-08-01T00:00:00.000Z");
    expect(form.credential).toBe("");
  });

  it("新增校验：必须先选 agent 类型再选套餐、起期必填", () => {
    const form = emptySubscriptionForm();
    form.startsAt = null;
    const errors = validateSubscriptionForm(form, "create");
    expect(errors).toContain("请选择 Agent 类型");
    expect(errors).toContain("请选择套餐");
    expect(errors).toContain("起期不能为空");
  });

  it("新增校验：选了 agent 类型但没选套餐仍提示选套餐", () => {
    const form = emptySubscriptionForm();
    form.agentType = "CLAUDE";
    expect(validateSubscriptionForm(form, "create")).toEqual(["请选择套餐"]);
  });

  it("编辑校验：不再要求选套餐，只要求起期", () => {
    const form = subscriptionToForm(sample);
    form.planId = null;
    expect(validateSubscriptionForm(form, "edit")).toEqual([]);
    form.startsAt = null;
    expect(validateSubscriptionForm(form, "edit")).toContain("起期不能为空");
  });

  it("新增入参：带套餐 id 与 UTC 起期串，凭据与备注去空白", () => {
    const form = emptySubscriptionForm(new Date("2026-08-01T00:00:00Z"));
    form.planId = 11;
    form.credential = "  sk-ant-x  ";
    form.remark = "  首月  ";
    const payload = buildSubscriptionCreatePayload(form);
    expect(payload).toEqual({
      planId: 11,
      enterpriseId: null,
      startsAt: "2026-08-01T00:00:00.000Z",
      accountEmail: "",
      credential: "sk-ant-x",
      remark: "首月",
    });
  });

  it("编辑入参：只有归属/起期/账号邮箱/凭据/备注，不含套餐字段", () => {
    const form = subscriptionToForm(sample);
    const payload = buildSubscriptionUpdatePayload(form);
    expect(payload).toEqual({
      enterpriseId: null,
      startsAt: "2026-08-01T00:00:00.000Z",
      accountEmail: "",
      credential: "",
      remark: "线下收款",
    });
  });

  it("未通过校验就构造入参是编程错误，直接抛出", () => {
    const form = emptySubscriptionForm();
    expect(() => buildSubscriptionCreatePayload(form)).toThrow();
    form.planId = 11;
    form.startsAt = null;
    expect(() => buildSubscriptionCreatePayload(form)).toThrow();
    expect(() => buildSubscriptionUpdatePayload(form)).toThrow();
  });

  it("止期推算：起期 + 套餐天数，按绝对毫秒不受时区影响", () => {
    const endsAt = computeEndsAt(new Date("2026-08-01T08:30:00Z"), 30);
    expect(endsAt.toISOString()).toBe("2026-08-31T08:30:00.000Z");
  });

  it("套餐下拉标签只带名称、时长与价格，不再混入 agent 类型", () => {
    expect(formatPlanLabel(plan)).toBe("Claude 月付（30 天 · 99.99 USD）");
  });

  it("agent 类型选项：只取有上架套餐的类型并去重，未知类型展示原始取值", () => {
    const codexPlan: PlanResponse = { ...plan, id: 12, name: "Codex 月付", agentType: "CODEX" };
    const disabledPlan: PlanResponse = { ...plan, id: 13, agentType: "FUTURE_AGENT", enabled: false };
    const futurePlan: PlanResponse = { ...plan, id: 14, agentType: "FUTURE_AGENT" };
    expect(agentTypeOptions([plan, codexPlan, { ...plan, id: 15 }, disabledPlan, futurePlan])).toEqual([
      { value: "CLAUDE", label: "Claude Code" },
      { value: "CODEX", label: "Codex" },
      { value: "FUTURE_AGENT", label: "FUTURE_AGENT" },
    ]);
  });

  it("套餐选项：只列所选 agent 类型下的上架套餐；未选类型时为空", () => {
    const codexPlan: PlanResponse = { ...plan, id: 12, name: "Codex 月付", agentType: "CODEX" };
    const disabledPlan: PlanResponse = { ...plan, id: 13, enabled: false };
    const all = [plan, codexPlan, disabledPlan];
    expect(planOptionsForAgent(all, "CLAUDE")).toEqual([
      { value: 11, label: "Claude 月付（30 天 · 99.99 USD）" },
    ]);
    expect(planOptionsForAgent(all, null)).toEqual([]);
  });

  it("企业选项：只列启用中且支持所选 agent 类型的企业，标签带域名", () => {
    const codexOnly: EnterpriseResponse = {
      ...enterprise,
      id: 22,
      name: "Codex 公司",
      domain: "codex.example",
      agentTypes: ["CODEX"],
    };
    const both: EnterpriseResponse = {
      ...enterprise,
      id: 23,
      name: "双栈公司",
      domain: "both.example",
      agentTypes: ["CLAUDE", "CODEX"],
    };
    const disabled: EnterpriseResponse = {
      ...enterprise,
      id: 24,
      name: "停用公司",
      domain: "off.example",
      enabled: false,
    };
    const all = [enterprise, codexOnly, both, disabled];

    expect(enterpriseOptionsForAgent(all, "CLAUDE")).toEqual([
      { value: 21, label: "Acme 科技（acme.com）" },
      { value: 23, label: "双栈公司（both.example）" },
    ]);
  });

  it("企业选项：未选 agent 类型时为空，逼着先选类型", () => {
    expect(enterpriseOptionsForAgent([enterprise], null)).toEqual([]);
  });

  it("回填带出归属企业；分配与更新的提交体都带 enterpriseId", () => {
    const form = subscriptionToForm({ ...sample, enterpriseId: 21 });
    expect(form.enterpriseId).toBe(21);

    expect(buildSubscriptionCreatePayload(form).enterpriseId).toBe(21);
    expect(buildSubscriptionUpdatePayload(form).enterpriseId).toBe(21);
  });

  it("未归属企业时提交体的 enterpriseId 是 null", () => {
    const form = subscriptionToForm(sample);
    expect(form.enterpriseId).toBeNull();
    expect(buildSubscriptionCreatePayload(form).enterpriseId).toBeNull();
    expect(buildSubscriptionUpdatePayload(form).enterpriseId).toBeNull();
  });

  it("空表单不预选归属企业", () => {
    expect(emptySubscriptionForm(new Date("2026-08-20T12:00:00Z")).enterpriseId).toBeNull();
  });

  it("账号邮箱选填：留空或全空白都放行，个人订阅、归属企业均然", () => {
    const form = emptySubscriptionForm();
    form.agentType = "CLAUDE";
    form.planId = 11;
    expect(validateSubscriptionForm(form, "create", null)).toEqual([]);

    form.accountEmail = "   ";
    expect(validateSubscriptionForm(form, "create", "acme.com")).toEqual([]);
  });

  it("账号邮箱填了就得是邮箱格式", () => {
    const form = subscriptionToForm(sample);
    form.accountEmail = "zhangsan";
    expect(validateSubscriptionForm(form, "edit", null)).toEqual(["账号邮箱格式不正确"]);
  });

  it("归属企业时账号邮箱域名须与企业域名一致，大小写与首尾空白不计", () => {
    const form = subscriptionToForm({ ...sample, enterpriseId: 21 });
    form.accountEmail = " Zhang@ACME.com ";
    expect(validateSubscriptionForm(form, "edit", "acme.com")).toEqual([]);

    form.accountEmail = "zhang@other.com";
    expect(validateSubscriptionForm(form, "edit", "acme.com")).toEqual([
      "账号邮箱域名须与企业域名 acme.com 一致",
    ]);
  });

  it("个人订阅不校验域名：任何域名的邮箱都放行", () => {
    const form = subscriptionToForm(sample);
    form.accountEmail = "zhang@other.com";
    expect(validateSubscriptionForm(form, "edit", null)).toEqual([]);
  });

  it("回填带出账号邮箱；未录时回填成空串", () => {
    expect(subscriptionToForm({ ...sample, accountEmail: "zhang@acme.com" }).accountEmail).toBe(
      "zhang@acme.com",
    );
    expect(subscriptionToForm(sample).accountEmail).toBe("");
  });

  it("提交体带账号邮箱：去空白并转小写，留空即清除", () => {
    const form = emptySubscriptionForm(new Date("2026-08-01T00:00:00Z"));
    form.planId = 11;
    form.accountEmail = "  Zhang@ACME.com  ";
    expect(buildSubscriptionCreatePayload(form).accountEmail).toBe("zhang@acme.com");
    expect(buildSubscriptionUpdatePayload(form).accountEmail).toBe("zhang@acme.com");

    form.accountEmail = "";
    expect(buildSubscriptionCreatePayload(form).accountEmail).toBe("");
    expect(buildSubscriptionUpdatePayload(form).accountEmail).toBe("");
  });
});
