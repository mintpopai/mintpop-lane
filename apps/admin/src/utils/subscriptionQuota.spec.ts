import { describe, expect, it } from "vitest";
import { quotaPercent } from "./subscriptionQuota";

describe("quotaPercent", () => {
  it("按 usedBytes / totalBytes 四舍五入出百分比", () => {
    expect(quotaPercent({ usedBytes: 36160899072, totalBytes: 137438953472 })).toBe(26);
  });

  it("用量超过总量时封顶 100：机场统计口径可能晚一拍", () => {
    expect(quotaPercent({ usedBytes: 200, totalBytes: 100 })).toBe(100);
  });

  it("机场没给额度头（null）时返回 null，不是 0", () => {
    expect(quotaPercent({ usedBytes: null, totalBytes: null })).toBeNull();
    expect(quotaPercent({ usedBytes: 5, totalBytes: null })).toBeNull();
  });

  it("totalBytes 为 0 或负数视同没有数据，避免除出 NaN / Infinity", () => {
    expect(quotaPercent({ usedBytes: 5, totalBytes: 0 })).toBeNull();
    expect(quotaPercent({ usedBytes: 5, totalBytes: -1 })).toBeNull();
  });
});
