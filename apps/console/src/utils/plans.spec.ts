import { describe, expect, it } from "vitest";
import type { PlanResponse } from "../api/types";
import { agentTabs, plansForAgent } from "./plans";

const claude: PlanResponse = {
  id: 1,
  name: "Claude 月付",
  agentType: "CLAUDE",
  durationDays: 30,
  price: 99.99,
  currency: "USD",
  description: null,
  imageUrl: null,
  detail: null,
};
const codex: PlanResponse = {
  id: 2,
  name: "Codex 月付",
  agentType: "CODEX",
  durationDays: 30,
  price: 49,
  currency: "USD",
  description: null,
  imageUrl: null,
  detail: null,
};

describe("plans", () => {
  it("tab 只列有套餐的 agent 类型，按首次出现去重并带计数；未知类型原样展示", () => {
    expect(
      agentTabs([claude, codex, { ...claude, id: 3 }, { ...codex, id: 4, agentType: "FUTURE" }]),
    ).toEqual([
      { value: "CLAUDE", label: "Claude Code", count: 2 },
      { value: "CODEX", label: "Codex", count: 1 },
      { value: "FUTURE", label: "FUTURE", count: 1 },
    ]);
  });

  it("按 agent 类型筛出套餐，保持服务端给的顺序", () => {
    expect(plansForAgent([claude, codex, { ...claude, id: 3 }], "CLAUDE").map((p) => p.id)).toEqual(
      [1, 3],
    );
    expect(plansForAgent([claude], "CODEX")).toEqual([]);
  });
});
