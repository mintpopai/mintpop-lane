/**
 * 登录环路熔断：记一次「刚跳去登录」的时间戳，用来识别
 * 「跳了登录→回来还是未登录→又跳登录」这种前端与 Logto 之间的静默死循环
 * （典型成因：会话密钥不匹配、Cookie 因跨域/协议种不上）。
 * 用 sessionStorage 而非 localStorage：标记只应活在当前标签页的这一次登录尝试里。
 */
const 标记键 = "lane.loginRedirectAt";

/** 判定窗口：上次跳登录距今不足这个时长，就认为是「刚跳过又立刻回来」 */
const 环路窗口毫秒 = 15_000;

/** 跳登录之前打标记 */
export function 标记登录跳转(now: number = Date.now()): void {
  sessionStorage.setItem(标记键, String(now));
}

/** 是否疑似环路：有标记且在判定窗口内 */
export function 疑似环路(now: number = Date.now()): boolean {
  const raw = sessionStorage.getItem(标记键);
  if (raw === null) {
    return false;
  }
  const 上次跳转 = Number(raw);
  // 标记被人为改坏时按「没标记」处理，宁可再试一次登录也不要误判成环路
  if (!Number.isFinite(上次跳转)) {
    return false;
  }
  return now - 上次跳转 < 环路窗口毫秒;
}

/** 登录成功、或用户主动重试时清掉标记 */
export function 清除标记(): void {
  sessionStorage.removeItem(标记键);
}
