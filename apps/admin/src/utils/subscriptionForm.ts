import type { AdminSubscriptionResponse, SubscriptionSaveRequest } from "../api/types";

/** 表单模型。agentType 用 string：回填未知类型时保留原值，避免打开即误改 */
export interface SubscriptionFormModel {
  id: number | null;
  agentType: string;
  name: string;
  /** el-date-picker 直接绑定 Date（按管理员本地时区取值）；null = 未填 */
  startsAt: Date | null;
  endsAt: Date | null;
  /** 留空表示沿用原凭据 */
  credential: string;
  remark: string;
}

export function emptySubscriptionForm(): SubscriptionFormModel {
  return {
    id: null,
    agentType: "CLAUDE",
    name: "",
    startsAt: null,
    endsAt: null,
    credential: "",
    remark: "",
  };
}

export function subscriptionToForm(s: AdminSubscriptionResponse): SubscriptionFormModel {
  return {
    id: s.id,
    agentType: s.agentType,
    name: s.name,
    // 服务端给的是带 Z 的 UTC 串，解析成 Date 后由控件按本地时区回显
    startsAt: new Date(s.startsAt),
    endsAt: new Date(s.endsAt),
    // 服务端不回传凭据，回填一律为空；提交时空串即表示不修改
    credential: "",
    remark: s.remark ?? "",
  };
}

export function validateSubscriptionForm(form: SubscriptionFormModel): string[] {
  const errors: string[] = [];
  const name = form.name.trim();
  if (!name) {
    errors.push("套餐名不能为空");
  } else if (name.length > 64) {
    errors.push("套餐名最长 64 个字符");
  }
  if (!form.startsAt) {
    errors.push("起期不能为空");
  }
  if (!form.endsAt) {
    errors.push("止期不能为空");
  }
  // 绝对时刻直接比 epoch；与服务端「止期必须晚于起期」判定一致
  if (form.startsAt && form.endsAt && form.endsAt.getTime() <= form.startsAt.getTime()) {
    errors.push("止期必须晚于起期");
  }
  return errors;
}

export function buildSubscriptionPayload(form: SubscriptionFormModel): SubscriptionSaveRequest {
  if (!form.startsAt || !form.endsAt) {
    // 调用契约：先过 validateSubscriptionForm；走到这里是编程错误
    throw new Error("起止期未填，先通过表单校验再构造入参");
  }
  return {
    agentType: form.agentType as SubscriptionSaveRequest["agentType"],
    name: form.name.trim(),
    // toISOString 天然输出 UTC（带 Z），提交即绝对时刻
    startsAt: form.startsAt.toISOString(),
    endsAt: form.endsAt.toISOString(),
    // 空串 = 沿用原值，服务端按空白转 null 处理
    credential: form.credential.trim(),
    remark: form.remark.trim(),
  };
}
