import { describe, expect, it } from "vitest";
import type { PlanResponse } from "../api/types";
import { buildPlanPayload, emptyPlanForm, planToForm, validatePlanForm } from "./planForm";

const plan: PlanResponse = {
  id: 3,
  name: "月付套餐",
  agentType: "CLAUDE",
  durationDays: 30,
  price: 29.9,
  currency: "USD",
  enabled: true,
  remark: "首发款",
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

describe("emptyPlanForm", () => {
  it("默认 USD、上架、其余留白", () => {
    const form = emptyPlanForm();
    expect(form).toEqual({
      name: "",
      agentType: "CLAUDE",
      durationDays: null,
      price: null,
      currency: "USD",
      enabled: true,
      remark: "",
    });
  });
});

describe("planToForm / buildPlanPayload", () => {
  it("套餐记录转表单再转回提交体，字段原样往返", () => {
    const form = planToForm(plan);
    expect(form.name).toBe("月付套餐");
    expect(form.durationDays).toBe(30);
    expect(form.price).toBe(29.9);

    expect(buildPlanPayload(form)).toEqual({
      name: "月付套餐",
      agentType: "CLAUDE",
      durationDays: 30,
      price: 29.9,
      currency: "USD",
      enabled: true,
      remark: "首发款",
    });
  });

  it("remark 为 null 的记录转表单后是空串；提交体名称与备注去除首尾空白", () => {
    const form = planToForm({ ...plan, remark: null });
    expect(form.remark).toBe("");

    form.name = "  月付套餐  ";
    form.remark = " 备注 ";
    const payload = buildPlanPayload(form);
    expect(payload.name).toBe("月付套餐");
    expect(payload.remark).toBe("备注");
  });
});

describe("planToForm 对未知 agentType 的兼容", () => {
  it("未知 agentType 回填时保留原值以免误改", () => {
    const form = planToForm({ ...plan, agentType: "FUTURE_AGENT" });
    expect(form.agentType).toBe("FUTURE_AGENT");
  });
});

describe("validatePlanForm", () => {
  it("合法表单无错误", () => {
    expect(validatePlanForm(planToForm(plan))).toEqual([]);
  });

  it("名称必填", () => {
    const form = planToForm(plan);
    form.name = "  ";
    expect(validatePlanForm(form)).toContain("套餐名不能为空");
  });

  it("时长必须是不小于 1 的整数", () => {
    const form = planToForm(plan);
    form.durationDays = null;
    expect(validatePlanForm(form)).toContain("时长必须是不小于 1 的整数（天）");
    form.durationDays = 0;
    expect(validatePlanForm(form)).toContain("时长必须是不小于 1 的整数（天）");
    form.durationDays = 1.5;
    expect(validatePlanForm(form)).toContain("时长必须是不小于 1 的整数（天）");
  });

  it("数字输入被清空时 v-model.number 会给出空串而非 null，同样要拦下", () => {
    const form = planToForm(plan);
    form.price = "" as unknown as number;
    expect(validatePlanForm(form)).toContain("价格必须是不小于 0 的数，至多两位小数");

    const form2 = planToForm(plan);
    form2.durationDays = "" as unknown as number;
    expect(validatePlanForm(form2)).toContain("时长必须是不小于 1 的整数（天）");
  });

  it("价格必须是不小于 0 的数，且至多两位小数", () => {
    const form = planToForm(plan);
    form.price = null;
    expect(validatePlanForm(form)).toContain("价格必须是不小于 0 的数，至多两位小数");
    form.price = -1;
    expect(validatePlanForm(form)).toContain("价格必须是不小于 0 的数，至多两位小数");
    form.price = 1.999;
    expect(validatePlanForm(form)).toContain("价格必须是不小于 0 的数，至多两位小数");
    form.price = 0;
    expect(validatePlanForm(form)).toEqual([]);
  });
});
