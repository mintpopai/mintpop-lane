/**
 * datetime-local 原生输入框的绑定层：控件值是「本地时区、到分钟」的
 * `YYYY-MM-DDTHH:mm` 串，表单模型仍是 Date | null（绝对时刻），
 * 两个方向的换算都收口在这里。
 */

/** Date → 控件值。null 给空串（未填） */
export function toDatetimeLocal(date: Date | null): string {
  if (!date) {
    return "";
  }
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 控件值 → Date。空串或非法串给 null；不带时区后缀的串由 Date 按本地时区解析 */
export function fromDatetimeLocal(value: string): Date | null {
  if (!value) {
    return null;
  }
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}
