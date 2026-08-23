import { describe, expect, it } from "vitest";
import type { AdminSubscriptionResponse, PlanResponse } from "../api/types";
import {
  buildSubscriptionCreatePayload,
  buildSubscriptionUpdatePayload,
  computeEndsAt,
  emptySubscriptionForm,
  formatPlanLabel,
  subscriptionToForm,
  validateSubscriptionForm,
} from "./subscriptionForm";

const sample: AdminSubscriptionResponse = {
  id: 3,
  assignmentNo: "0f8fad5bd9cb469fa16570867728950e",
  userId: 7,
  agentType: "CLAUDE",
  planId: 11,
  name: "Claude 月付",
  planDurationDays: 30,
  planPrice: 99.99,
  planCurrency: "USD",
  startsAt: "2026-08-01T00:00:00Z",
  endsAt: "2026-08-31T00:00:00Z",
  hasCredential: true,
  remark: "线下收款",
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

const plan: PlanResponse = {
  id: 11,
  name: "Claude 月付",
  durationDays: 30,
  price: 99.99,
  currency: "USD",
  enabled: true,
  remark: null,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

describe("subscriptionForm", () => {
  it("空表单默认 CLAUDE、未选套餐、起期取当下", () => {
    const now = new Date("2026-08-20T12:00:00Z");
    const form = emptySubscriptionForm(now);
    expect(form.id).toBeNull();
    expect(form.agentType).toBe("CLAUDE");
    expect(form.planId).toBeNull();
    expect(form.startsAt).toEqual(now);
    expect(form.credential).toBe("");
  });

  it("回填把 UTC 串解析成 Date，带上套餐 id，不含凭据", () => {
    const form = subscriptionToForm(sample);
    expect(form.planId).toBe(11);
    expect(form.startsAt?.toISOString()).toBe("2026-08-01T00:00:00.000Z");
    expect(form.credential).toBe("");
  });

  it("新增校验：必须选套餐、起期必填", () => {
    const form = emptySubscriptionForm();
    form.startsAt = null;
    const errors = validateSubscriptionForm(form, "create");
    expect(errors).toContain("请选择套餐");
    expect(errors).toContain("起期不能为空");
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
      agentType: "CLAUDE",
      planId: 11,
      startsAt: "2026-08-01T00:00:00.000Z",
      credential: "sk-ant-x",
      remark: "首月",
    });
  });

  it("编辑入参：只有起期/凭据/备注，不含套餐字段", () => {
    const form = subscriptionToForm(sample);
    const payload = buildSubscriptionUpdatePayload(form);
    expect(payload).toEqual({
      startsAt: "2026-08-01T00:00:00.000Z",
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

  it("套餐下拉标签带名称、时长与价格", () => {
    expect(formatPlanLabel(plan)).toBe("Claude 月付（30 天 · 99.99 USD）");
  });

  it("未知 agentType 回填时保留原值以免误改", () => {
    const form = subscriptionToForm({ ...sample, agentType: "FUTURE_AGENT" });
    expect(form.agentType).toBe("FUTURE_AGENT");
  });
});
