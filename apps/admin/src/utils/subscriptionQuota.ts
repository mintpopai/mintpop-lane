import type { AirportSubscriptionResponse } from "../api/types";

/**
 * 订阅的流量用量百分比。null 表示「该机场没提供额度头」，与 0% 是两回事——
 * 不能把「没数据」显示成「用了 0%」。total 缺失或非正也一并视为没有数据，
 * 避免除出 NaN / Infinity。封顶 100：机场统计口径可能比订阅端晚一拍，用量偶尔会略超总量。
 */
export function quotaPercent(
  sub: Pick<AirportSubscriptionResponse, "usedBytes" | "totalBytes">,
): number | null {
  if (sub.usedBytes === null || sub.totalBytes === null || sub.totalBytes <= 0) {
    return null;
  }
  return Math.min(100, Math.round((sub.usedBytes / sub.totalBytes) * 100));
}
