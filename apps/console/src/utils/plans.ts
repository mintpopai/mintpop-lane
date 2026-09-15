import type { PlanResponse } from "../api/types";
import { agentLabel } from "./format";

export interface AgentTab {
  value: string;
  label: string;
  count: number;
}

/** 一级 tab：只列有套餐的 agent 类型，顺序按服务端返回的首次出现 */
export function agentTabs(plans: PlanResponse[]): AgentTab[] {
  const tabs: AgentTab[] = [];
  for (const plan of plans) {
    const tab = tabs.find((t) => t.value === plan.agentType);
    if (tab) {
      tab.count += 1;
    } else {
      tabs.push({ value: plan.agentType, label: agentLabel(plan.agentType), count: 1 });
    }
  }
  return tabs;
}

/** 按 agent 类型筛出套餐，保持服务端给的顺序 */
export function plansForAgent(plans: PlanResponse[], agentType: string | null): PlanResponse[] {
  return plans.filter((p) => p.agentType === agentType);
}
