/** 列表里空值统一显示这个，避免出现空白单元格或 undefined */
export const PLACEHOLDER = "—";

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

/** 列表拼接，空列表给占位符 */
export function joinOrDash(list?: string[] | null, separator = "、"): string {
  if (!list || list.length === 0) {
    return PLACEHOLDER;
  }
  return list.join(separator);
}

/** 布尔值 → 中文标签 */
export function booleanLabel(value: boolean, truthy: string, falsy: string): string {
  return value ? truthy : falsy;
}
