import { describe, expect, it } from "vitest";
import { fromDatetimeLocal, toDatetimeLocal } from "./datetimeLocal";

describe("toDatetimeLocal", () => {
  it("null 给空串，供未填的 datetime-local 输入框回显", () => {
    expect(toDatetimeLocal(null)).toBe("");
  });

  it("按本地时区输出到分钟，各段补零", () => {
    // 用本地时区构造，输出必须逐字段一致，与浏览器时区无关
    const d = new Date(2026, 0, 5, 8, 7);
    expect(toDatetimeLocal(d)).toBe("2026-01-05T08:07");
  });
});

describe("fromDatetimeLocal", () => {
  it("空串给 null，表示未填", () => {
    expect(fromDatetimeLocal("")).toBeNull();
  });

  it("非法串给 null，不产出 Invalid Date", () => {
    expect(fromDatetimeLocal("垃圾")).toBeNull();
  });

  it("按本地时区解析控件值", () => {
    const d = fromDatetimeLocal("2026-01-05T08:07");
    expect(d).not.toBeNull();
    expect(d!.getFullYear()).toBe(2026);
    expect(d!.getMonth()).toBe(0);
    expect(d!.getDate()).toBe(5);
    expect(d!.getHours()).toBe(8);
    expect(d!.getMinutes()).toBe(7);
  });

  it("与 toDatetimeLocal 互为往返（精确到分钟）", () => {
    const d = new Date(2026, 7, 20, 23, 59);
    expect(fromDatetimeLocal(toDatetimeLocal(d))!.getTime()).toBe(d.getTime());
  });
});
