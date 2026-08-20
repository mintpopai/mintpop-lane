import type { AdminSubscriptionResponse, SubscriptionSaveRequest } from "../api/types";

/** 表单模型。agentType 用 string：回填未知类型时保留原值，避免打开即误改 */
export interface SubscriptionFormModel {
  id: number | null;
  agentType: string;
  name: string;
  startsAt: string;
  endsAt: string;
  /** 留空表示沿用原凭据 */
  credential: string;
  remark: string;
}

export function emptySubscriptionForm(): SubscriptionFormModel {
  return {
    id: null,
    agentType: "CLAUDE",
    name: "",
    startsAt: "",
    endsAt: "",
    credential: "",
    remark: "",
  };
}

export function subscriptionToForm(s: AdminSubscriptionResponse): SubscriptionFormModel {
  return {
    id: s.id,
    agentType: s.agentType,
    name: s.name,
    startsAt: s.startsAt,
    endsAt: s.endsAt,
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
  // ISO 字符串可按字典序比较；与服务端「止期必须晚于起期」判定一致
  if (form.startsAt && form.endsAt && form.endsAt <= form.startsAt) {
    errors.push("止期必须晚于起期");
  }
  return errors;
}

export function buildSubscriptionPayload(form: SubscriptionFormModel): SubscriptionSaveRequest {
  return {
    agentType: form.agentType as SubscriptionSaveRequest["agentType"],
    name: form.name.trim(),
    startsAt: form.startsAt,
    endsAt: form.endsAt,
    // 空串 = 沿用原值，服务端按空白转 null 处理
    credential: form.credential.trim(),
    remark: form.remark.trim(),
  };
}
