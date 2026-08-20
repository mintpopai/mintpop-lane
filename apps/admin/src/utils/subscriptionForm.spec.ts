import { describe, expect, it } from "vitest";
import type { AdminSubscriptionResponse } from "../api/types";
import {
  buildSubscriptionPayload,
  emptySubscriptionForm,
  subscriptionToForm,
  validateSubscriptionForm,
} from "./subscriptionForm";

const 样例: AdminSubscriptionResponse = {
  id: 3,
  userId: 7,
  agentType: "CLAUDE",
  name: "Claude 席位 1",
  startsAt: "2026-08-01T00:00:00",
  endsAt: "2026-09-01T00:00:00",
  hasCredential: true,
  remark: "线下收款",
  createdAt: "2026-08-01T00:00:00",
  updatedAt: "2026-08-01T00:00:00",
};

describe("subscriptionForm", () => {
  it("空表单默认 CLAUDE 且凭据为空", () => {
    const form = emptySubscriptionForm();
    expect(form.id).toBeNull();
    expect(form.agentType).toBe("CLAUDE");
    expect(form.credential).toBe("");
  });

  it("回填不含凭据，提交空凭据表示沿用原值", () => {
    const form = subscriptionToForm(样例);
    expect(form.credential).toBe("");
    expect(buildSubscriptionPayload(form).credential).toBe("");
  });

  it("校验：套餐名必填、超长拒绝、止期必须晚于起期", () => {
    const form = emptySubscriptionForm();
    form.startsAt = "2026-09-01T00:00:00";
    form.endsAt = "2026-09-01T00:00:00";
    const errors = validateSubscriptionForm(form);
    expect(errors).toContain("套餐名不能为空");
    expect(errors).toContain("止期必须晚于起期");

    form.name = "长".repeat(65);
    expect(validateSubscriptionForm(form)).toContain("套餐名最长 64 个字符");
  });

  it("合法表单校验通过并生成裁剪后的入参", () => {
    const form = subscriptionToForm(样例);
    form.name = "  Claude 席位 1  ";
    expect(validateSubscriptionForm(form)).toEqual([]);
    const payload = buildSubscriptionPayload(form);
    expect(payload.name).toBe("Claude 席位 1");
    expect(payload.startsAt).toBe("2026-08-01T00:00:00");
  });

  it("未知 agentType 回填时保留原值以免误改", () => {
    const form = subscriptionToForm({ ...样例, agentType: "FUTURE_AGENT" });
    expect(form.agentType).toBe("FUTURE_AGENT");
  });
});
