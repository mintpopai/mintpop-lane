/**
 * 登录环路熔断：记一次「刚跳去登录」的时间戳，用来识别
 * 「跳了登录→回来还是未登录→又跳登录」这种前端与 Logto 之间的静默死循环
 * （典型成因：会话密钥不匹配、Cookie 因跨域/协议种不上）。
 * 用 sessionStorage 而非 localStorage：标记只应活在当前标签页的这一次登录尝试里。
 */
const MARK_KEY = "lane.loginRedirectAt";

/** 判定窗口：上次跳登录距今不足这个时长，就认为是「刚跳过又立刻回来」 */
const LOOP_WINDOW_MS = 15_000;

/** 跳登录之前打标记 */
export function markLoginRedirect(now: number = Date.now()): void {
  sessionStorage.setItem(MARK_KEY, String(now));
}

/** 是否疑似环路：有标记且在判定窗口内 */
export function isLikelyLoginLoop(now: number = Date.now()): boolean {
  const raw = sessionStorage.getItem(MARK_KEY);
  if (raw === null) {
    return false;
  }
  const lastRedirectAt = Number(raw);
  // 标记被人为改坏时按「没标记」处理，宁可再试一次登录也不要误判成环路
  if (!Number.isFinite(lastRedirectAt)) {
    return false;
  }
  return now - lastRedirectAt < LOOP_WINDOW_MS;
}

/** 登录成功、或用户主动重试时清掉标记 */
export function clearLoginMark(): void {
  sessionStorage.removeItem(MARK_KEY);
}
