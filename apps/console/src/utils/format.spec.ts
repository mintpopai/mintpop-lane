import { describe, expect, it } from "vitest";
import type { MeSubscription } from "../api/types";
import {
  agentLabel,
  formatAmount,
  formatAssignmentNo,
  formatDateTime,
  subscriptionState,
} from "./format";

const sub: MeSubscription = {
  id: 1,
  assignmentNo: "7K3M9QX2FT",
  name: "Claude 月付",
  agentType: "CLAUDE",
  startsAt: "2026-08-01T00:00:00Z",
  endsAt: "2026-08-31T00:00:00Z",
  active: true,
};

describe("format", () => {
  it("金额：最小单位整数按币种缩两位小数，带币种代码", () => {
    expect(formatAmount(9999, "USD")).toBe("99.99 USD");
    expect(formatAmount(50, "CNY")).toBe("0.50 CNY");
    expect(formatAmount(100000, "USD")).toBe("1000.00 USD");
  });

  it("空时间显示占位符，不显示 Invalid Date", () => {
    expect(formatDateTime(null)).toBe("—");
    expect(formatDateTime("not a date")).toBe("—");
    expect(formatDateTime("2026-08-01T00:00:00Z")).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/);
  });

  it("分配号从中间劈开成两组便于口述", () => {
    expect(formatAssignmentNo("7K3M9QX2FT")).toBe("7K3M9-QX2FT");
    expect(formatAssignmentNo("short")).toBe("short");
  });

  it("agent 类型未知时原样展示", () => {
    expect(agentLabel("CLAUDE")).toBe("Claude Code");
    expect(agentLabel("FUTURE")).toBe("FUTURE");
  });

  it("订阅三档状态：起止为空是待开通，在期看服务端 active，其余是已过期", () => {
    expect(subscriptionState({ ...sub, startsAt: null, endsAt: null, active: false })).toBe(
      "PENDING",
    );
    expect(subscriptionState(sub)).toBe("ACTIVE");
    expect(subscriptionState({ ...sub, active: false })).toBe("EXPIRED");
  });
});
