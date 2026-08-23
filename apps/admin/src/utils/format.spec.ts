import { describe, expect, it } from "vitest";
import { agentLabel, booleanLabel, formatDate, formatDateTime } from "./format";

// 钉死本进程时区，让「按本地时区渲染」可断言（Node 在 POSIX 上支持运行中生效）
process.env.TZ = "Asia/Shanghai";

describe("formatDateTime", () => {
  it("把服务端的 UTC 时间串按本地时区渲染到分钟", () => {
    // UTC 02:20 = 北京时间 10:20
    expect(formatDateTime("2026-08-18T02:20:30Z")).toBe("2026-08-18 10:20");
  });

  it("跨日换算正确——UTC 傍晚是北京次日凌晨", () => {
    expect(formatDateTime("2026-08-18T17:00:00Z")).toBe("2026-08-19 01:00");
  });

  it("空值显示占位符，不显示 undefined", () => {
    expect(formatDateTime(null)).toBe("—");
    expect(formatDateTime(undefined)).toBe("—");
    expect(formatDateTime("")).toBe("—");
  });

  it("解析不了的串显示占位符，不显示 Invalid Date", () => {
    expect(formatDateTime("不是时间")).toBe("—");
  });
});

describe("formatDate", () => {
  it("把服务端的 UTC 时间串按本地时区渲染成日期", () => {
    // UTC 02:20 = 北京时间 10:20，仍是同一天
    expect(formatDate("2026-08-18T02:20:30Z")).toBe("2026-08-18");
  });

  it("跨日换算正确——UTC 傍晚是北京次日凌晨，日期要进位", () => {
    // UTC 08-31 17:00 = 北京 09-01 01:00
    expect(formatDate("2026-08-31T17:00:00Z")).toBe("2026-09-01");
  });

  it("空值显示占位符，不显示 undefined", () => {
    expect(formatDate(null)).toBe("—");
    expect(formatDate(undefined)).toBe("—");
    expect(formatDate("")).toBe("—");
  });

  it("解析不了的串显示占位符，不显示 Invalid Date", () => {
    expect(formatDate("不是时间")).toBe("—");
  });
});

describe("booleanLabel", () => {
  it("按真假给出中文标签", () => {
    expect(booleanLabel(true, "已配置", "未配置")).toBe("已配置");
    expect(booleanLabel(false, "已配置", "未配置")).toBe("未配置");
  });
});

describe("agentLabel", () => {
  it("已知类型换成中文标签", () => {
    expect(agentLabel("CLAUDE")).toBe("Claude Code");
    expect(agentLabel("CODEX")).toBe("Codex");
  });

  it("服务端新增的未知类型原样展示，不显示空白或「未知」", () => {
    expect(agentLabel("GEMINI_CLI")).toBe("GEMINI_CLI");
  });
});
