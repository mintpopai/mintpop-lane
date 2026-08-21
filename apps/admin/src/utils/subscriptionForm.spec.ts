import { describe, expect, it } from "vitest";
import type { AdminSubscriptionResponse } from "../api/types";
import {
  buildSubscriptionPayload,
  emptySubscriptionForm,
  subscriptionToForm,
  validateSubscriptionForm,
} from "./subscriptionForm";

const sample: AdminSubscriptionResponse = {
  id: 3,
  userId: 7,
  agentType: "CLAUDE",
  name: "Claude 席位 1",
  startsAt: "2026-08-01T00:00:00Z",
  endsAt: "2026-09-01T00:00:00Z",
  hasCredential: true,
  remark: "线下收款",
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

describe("subscriptionForm", () => {
  it("空表单默认 CLAUDE、起止期为 null、凭据为空", () => {
    const form = emptySubscriptionForm();
    expect(form.id).toBeNull();
    expect(form.agentType).toBe("CLAUDE");
    expect(form.startsAt).toBeNull();
    expect(form.endsAt).toBeNull();
    expect(form.credential).toBe("");
  });

  it("回填把 UTC 串解析成 Date，不含凭据", () => {
    const form = subscriptionToForm(sample);
    expect(form.startsAt?.toISOString()).toBe("2026-08-01T00:00:00.000Z");
    expect(form.credential).toBe("");
  });

  it("校验：套餐名必填、超长拒绝、止期必须晚于起期（相等也拒绝）", () => {
    const form = emptySubscriptionForm();
    form.startsAt = new Date("2026-09-01T00:00:00Z");
    form.endsAt = new Date("2026-09-01T00:00:00Z");
    const errors = validateSubscriptionForm(form);
    expect(errors).toContain("套餐名不能为空");
    expect(errors).toContain("止期必须晚于起期");

    form.name = "长".repeat(65);
    expect(validateSubscriptionForm(form)).toContain("套餐名最长 64 个字符");
  });

  it("合法表单生成 UTC 串入参——提交往返不漂移", () => {
    const form = subscriptionToForm(sample);
    form.name = "  Claude 席位 1  ";
    expect(validateSubscriptionForm(form)).toEqual([]);
    const payload = buildSubscriptionPayload(form);
    expect(payload.name).toBe("Claude 席位 1");
    expect(payload.startsAt).toBe("2026-08-01T00:00:00.000Z");
    expect(payload.endsAt).toBe("2026-09-01T00:00:00.000Z");
  });

  it("未通过校验就构造入参是编程错误，直接抛出", () => {
    expect(() => buildSubscriptionPayload(emptySubscriptionForm())).toThrow();
  });

  it("未知 agentType 回填时保留原值以免误改", () => {
    const form = subscriptionToForm({ ...sample, agentType: "FUTURE_AGENT" });
    expect(form.agentType).toBe("FUTURE_AGENT");
  });
});
