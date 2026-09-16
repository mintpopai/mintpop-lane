import { afterEach, describe, expect, it, vi } from "vitest";
import {
  agentLabel,
  booleanLabel,
  deviceLabel,
  formatAssignmentNo,
  formatDate,
  formatDateTime,
  PLACEHOLDER,
  relativeTime,
} from "./format";

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

describe("formatAssignmentNo", () => {
  it("10 位短码从中间劈成两组", () => {
    expect(formatAssignmentNo("7K3M9QX2FT")).toBe("7K3M9-QX2FT");
  });

  it("长度不是 10 的原样返回，不硬拆", () => {
    expect(formatAssignmentNo("ABC")).toBe("ABC");
    expect(formatAssignmentNo("a3f19c2b8e5f4d1a9c37b20e6f8d4a11")).toBe(
      "a3f19c2b8e5f4d1a9c37b20e6f8d4a11",
    );
  });

  it("空值走占位符", () => {
    expect(formatAssignmentNo(null)).toBe(PLACEHOLDER);
    expect(formatAssignmentNo("")).toBe(PLACEHOLDER);
  });
});

describe("deviceLabel", () => {
  it("三要素齐全时压成「名称（系统 · 机型）」", () => {
    expect(deviceLabel({ name: "月白的 MacBook", os: "macos 26.6", model: "Mac17,9" })).toBe(
      "月白的 MacBook（macos 26.6 · Mac17,9）",
    );
  });

  it("机型为空时连分隔符一起去掉，不留下吊着的「 · 」", () => {
    // 桌面端读不到硬件型号（如 Windows 的 SystemProductName 读取失败）时机型就是空串
    expect(deviceLabel({ name: "DESKTOP-4F2", os: "windows 11", model: "" })).toBe(
      "DESKTOP-4F2（windows 11）",
    );
  });

  it("机型只有空白同样按空处理", () => {
    expect(deviceLabel({ name: "DESKTOP-4F2", os: "windows 11", model: "   " })).toBe(
      "DESKTOP-4F2（windows 11）",
    );
  });
});

describe("relativeTime", () => {
  // 「最近活跃」要回答的是「这台机器还在不在用」，绝对时刻还得让人心算，相对时刻一眼就知道
  const now = new Date("2026-09-15T12:00:00Z");

  afterEach(() => {
    vi.useRealTimers();
  });

  const at = (iso: string) => {
    vi.useFakeTimers();
    vi.setSystemTime(now);
    return relativeTime(iso);
  };

  it("一分钟以内显示「刚刚」", () => {
    expect(at("2026-09-15T11:59:31Z")).toBe("刚刚");
  });

  it("一小时以内按分钟", () => {
    expect(at("2026-09-15T11:17:00Z")).toBe("43 分钟前");
  });

  it("一天以内按小时", () => {
    expect(at("2026-09-15T09:30:00Z")).toBe("2 小时前");
  });

  it("三十天以内按天", () => {
    expect(at("2026-09-10T12:00:00Z")).toBe("5 天前");
  });

  it("超过三十天退回绝对日期：那么久以前，「87 天前」不如直接给日期好读", () => {
    expect(at("2026-06-20T12:00:00Z")).toBe("2026-06-20");
  });

  it("时刻在未来时按「刚刚」处理，不显示负数：服务端与本机时钟总会有些偏差", () => {
    expect(at("2026-09-15T12:00:30Z")).toBe("刚刚");
  });

  it("空值与非法串给占位符", () => {
    expect(relativeTime(null)).toBe(PLACEHOLDER);
    expect(relativeTime("")).toBe(PLACEHOLDER);
    expect(relativeTime("不是时间")).toBe(PLACEHOLDER);
  });
});
