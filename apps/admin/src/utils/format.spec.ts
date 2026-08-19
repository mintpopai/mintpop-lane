import { describe, expect, it } from "vitest";
import { booleanLabel, formatDateTime, joinOrDash } from "./format";

describe("formatDateTime", () => {
  it("把服务端的 ISO 时间串裁成到分钟的可读形式", () => {
    expect(formatDateTime("2026-08-18T10:20:30")).toBe("2026-08-18 10:20");
  });

  it("带毫秒也照样裁", () => {
    expect(formatDateTime("2026-08-18T10:20:30.123")).toBe("2026-08-18 10:20");
  });

  it("空值显示占位符，不显示 undefined", () => {
    expect(formatDateTime(null)).toBe("—");
    expect(formatDateTime(undefined)).toBe("—");
    expect(formatDateTime("")).toBe("—");
  });
});

describe("joinOrDash", () => {
  it("拼接非空列表", () => {
    expect(joinOrDash(["1.2.3.4", "5.6.7.8"])).toBe("1.2.3.4、5.6.7.8");
  });

  it("空列表显示占位符——未分配落地出口的用户很常见，不能显示成空白", () => {
    expect(joinOrDash([])).toBe("—");
    expect(joinOrDash(null)).toBe("—");
  });
});

describe("booleanLabel", () => {
  it("按真假给出中文标签", () => {
    expect(booleanLabel(true, "已配置", "未配置")).toBe("已配置");
    expect(booleanLabel(false, "已配置", "未配置")).toBe("未配置");
  });
});
