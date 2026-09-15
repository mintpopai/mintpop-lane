import { AGENT_TYPE_LABELS, type MeSubscription } from "../api/types";

export const PLACEHOLDER = "—";

/** agent 类型 → 中文标签；服务端新增的未知类型原样展示 */
export function agentLabel(agentType: string): string {
  return AGENT_TYPE_LABELS[agentType as keyof typeof AGENT_TYPE_LABELS] ?? agentType;
}

/** 带 Z 的 UTC 串按浏览器本地时区渲染到分钟 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return PLACEHOLDER;
  }
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) {
    return PLACEHOLDER;
  }
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 分配号展示形态：10 位短码劈成两组（7K3M9-QX2FT）；长度不对原样返回 */
export function formatAssignmentNo(value?: string | null): string {
  if (!value) {
    return PLACEHOLDER;
  }
  return value.length === 10 ? `${value.slice(0, 5)}-${value.slice(5)}` : value;
}

/** 最小货币单位整数 → 「99.99 USD」。USD / CNY 都是两位小数，与服务端 Currency.minorUnitScale 一致 */
export function formatAmount(amountMinor: number, currency: string): string {
  return `${(amountMinor / 100).toFixed(2)} ${currency}`;
}

export type SubscriptionState = "PENDING" | "ACTIVE" | "EXPIRED";

/** 起止为空 = 待开通；在期由服务端算好；其余（未到期或已过期）在用户视角都是「已过期 / 未生效」，用同一档 */
export function subscriptionState(s: MeSubscription): SubscriptionState {
  if (s.startsAt === null || s.endsAt === null) {
    return "PENDING";
  }
  return s.active ? "ACTIVE" : "EXPIRED";
}
