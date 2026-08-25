import { AGENT_TYPE_LABELS } from "../api/types";

/** 列表里空值统一显示这个，避免出现空白单元格或 undefined */
export const PLACEHOLDER = "—";

/**
 * agent 类型 → 界面上的中文标签。
 * 服务端可能新增本前端还不认识的类型，那就原样展示取值——比显示空白或「未知」有用。
 */
export function agentLabel(agentType: string): string {
  return AGENT_TYPE_LABELS[agentType as keyof typeof AGENT_TYPE_LABELS] ?? agentType;
}

/**
 * 服务端时间一律是带 Z 的 UTC 绝对时刻串（如 `2026-08-18T02:20:30Z`）。
 * 带 Z 的串交给 Date 解析没有歧义，这里换算成浏览器本地时区渲染到分钟——
 * 谁在看就按谁的时区显示，与部署环境时区无关。
 */
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

/**
 * 与 formatDateTime 同套路，只渲染本地日期部分（不带时分）。
 * 带 Z 的 UTC 串裁字符串直接取日期会错——UTC 与本地时区可能跨日
 * （如 UTC 傍晚已是本地次日凌晨），必须先按本地时区换算再取年月日。
 */
export function formatDate(value?: string | null): string {
  if (!value) {
    return PLACEHOLDER;
  }
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) {
    return PLACEHOLDER;
  }
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 布尔值 → 中文标签 */
export function booleanLabel(value: boolean, truthy: string, falsy: string): string {
  return value ? truthy : falsy;
}

/**
 * 分配号 → 展示形态：10 位短码从中间劈开成两组（`7K3M9-QX2FT`），照着念、照着抄都不容易串行。
 * 连字符只是展示，库里存的是不带连字符的原值——复制按钮复制的也是原值，
 * 这样粘进任何搜索框都能直接命中。长度不是 10 的（历史数据或服务端换了口径）原样返回，不硬拆。
 */
export function formatAssignmentNo(value?: string | null): string {
  if (!value) {
    return PLACEHOLDER;
  }
  return value.length === 10 ? `${value.slice(0, 5)}-${value.slice(5)}` : value;
}
