/** 列表里空值统一显示这个，避免出现空白单元格或 undefined */
export const PLACEHOLDER = "—";

/**
 * 服务端的 LocalDateTime 序列化成 `2026-08-18T10:20:30` 这样的 ISO 串。
 * 这里不做时区换算——服务端与部署机同在一个时区，字符串按原样裁到分钟即可，
 * 交给 Date 反而会引入「本地时区解释」的意外偏移。
 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return PLACEHOLDER;
  }
  const [date, time = ""] = value.split("T");
  return `${date} ${time.slice(0, 5)}`.trim();
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
