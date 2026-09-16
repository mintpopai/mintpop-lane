import { AGENT_TYPE_LABELS } from "../api/types";
import type { DeviceBrief } from "../api/types";

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

/**
 * 设备三要素压成一行：主机名是主角，系统与机型是辨认用的补充。
 *
 * 机型可能是空串——桌面端读不到硬件型号时就下发空值（库里该列 NOT NULL DEFAULT ''，
 * 绑定入参也刻意不强制它）。无条件拼「系统 · 机型」会留下一个吊着的分隔符
 * （`DESKTOP-4F2（windows 11 · ）`），故机型为空时连分隔符一起去掉。
 * 与服务端 DeviceRebindNotifyService.describe 同一口径，两处显示一致。
 */
export function deviceLabel(device: Pick<DeviceBrief, "name" | "os" | "model">): string {
  const model = device.model.trim();
  return `${device.name}（${device.os}${model ? ` · ${model}` : ""}）`;
}

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;
/** 超过这个跨度就不再说「N 天前」——那么久以前，具体哪天比「多少天前」好读 */
const RELATIVE_LIMIT_DAYS = 30;

/**
 * 绝对时刻 → 相对说法（「43 分钟前」「5 天前」）。
 *
 * 给「最近活跃」这类要回答「还在不在用」的字段用：管理员看的是新旧程度，
 * 绝对时刻还得拿今天去减，相对说法一眼就知道。需要精确时刻的地方仍用 formatDateTime，
 * 界面上把它挂在 title 上供悬浮查看。
 *
 * 未来时刻按「刚刚」处理而不是显示负数：服务端与本机时钟总会有几秒偏差，
 * 「-1 分钟前」只会让人以为是 bug。
 */
export function relativeTime(value?: string | null): string {
  if (!value) {
    return PLACEHOLDER;
  }
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) {
    return PLACEHOLDER;
  }
  const elapsed = Date.now() - d.getTime();
  if (elapsed < MINUTE_MS) {
    return "刚刚";
  }
  if (elapsed < HOUR_MS) {
    return `${Math.floor(elapsed / MINUTE_MS)} 分钟前`;
  }
  if (elapsed < DAY_MS) {
    return `${Math.floor(elapsed / HOUR_MS)} 小时前`;
  }
  if (elapsed < RELATIVE_LIMIT_DAYS * DAY_MS) {
    return `${Math.floor(elapsed / DAY_MS)} 天前`;
  }
  return formatDate(value);
}
