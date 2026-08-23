import type { Currency, PlanResponse, PlanSaveRequest } from "../api/types";

/** 套餐表单模型。数字输入未填时为 null，提交前经 validatePlanForm 拦住 */
export interface PlanFormModel {
  name: string;
  durationDays: number | null;
  price: number | null;
  currency: Currency;
  enabled: boolean;
  remark: string;
}

export function emptyPlanForm(): PlanFormModel {
  return {
    name: "",
    durationDays: null,
    price: null,
    currency: "USD",
    enabled: true,
    remark: "",
  };
}

export function planToForm(plan: PlanResponse): PlanFormModel {
  return {
    name: plan.name,
    durationDays: plan.durationDays,
    price: plan.price,
    currency: plan.currency,
    enabled: plan.enabled,
    remark: plan.remark ?? "",
  };
}

export function validatePlanForm(form: PlanFormModel): string[] {
  const errors: string[] = [];
  if (!form.name.trim()) {
    errors.push("套餐名不能为空");
  }
  // Number.isInteger 对 null / 空串都返回 false，「未填」与「非整数」一并挡下
  if (!Number.isInteger(form.durationDays) || (form.durationDays as number) < 1) {
    errors.push("时长必须是不小于 1 的整数（天）");
  }
  // 两位小数按「乘 100 后是整数」判定，浮点乘法误差给 1e-6 容差（如 29.9 * 100 = 2989.9999…）。
  // 不用 === null 判「未填」：v-model.number 在输入框被清空时给出的是空串而非 null
  if (
    typeof form.price !== "number" ||
    form.price < 0 ||
    Math.abs(form.price * 100 - Math.round(form.price * 100)) > 1e-6
  ) {
    errors.push("价格必须是不小于 0 的数，至多两位小数");
  }
  return errors;
}

export function buildPlanPayload(form: PlanFormModel): PlanSaveRequest {
  return {
    name: form.name.trim(),
    // 校验已保证非 null，这里的 ?? 只为收窄类型
    durationDays: form.durationDays ?? 0,
    price: form.price ?? 0,
    currency: form.currency,
    enabled: form.enabled,
    remark: form.remark.trim(),
  };
}
