import { AGENT_TYPE_LABELS } from "../api/types";
import type {
  AdminSubscriptionResponse,
  EnterpriseResponse,
  PlanResponse,
  SubscriptionCreateRequest,
  SubscriptionUpdateRequest,
} from "../api/types";

/**
 * 表单模型。套餐名/时长/价格不进表单——它们由所选套餐决定，
 * 编辑时是分配当时的快照、不可改。
 */
export interface SubscriptionFormModel {
  id: number | null;
  /** 先选 agent 类型、再从该类型下选套餐；null = 未选（仅新增模式需要选） */
  agentType: string | null;
  /** 所选套餐 id；null = 未选（仅新增模式需要选） */
  planId: number | null;
  /** 归属企业 id；null = 个人订阅。可选，不参与校验 */
  enterpriseId: number | null;
  /** 模型存 Date（绝对时刻）；datetime-local 控件值经 utils/datetimeLocal 换算；null = 未填 */
  startsAt: Date | null;
  /** 分配出去的账号邮箱，选填。与凭据不同：明文可见，留空即清除 */
  accountEmail: string;
  /** 留空表示沿用原凭据 */
  credential: string;
  remark: string;
}

/** 只做「有且只有一个 @、两侧非空、域名带点」的粗筛，细节交服务端的 @Email */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** 取邮箱的域名段；调用前先过 EMAIL_PATTERN，必有 @ */
function domainOf(email: string): string {
  return email.slice(email.lastIndexOf("@") + 1);
}

/** 空表单：起期默认取当下（分配即生效），now 参数供测试注入 */
export function emptySubscriptionForm(now: Date = new Date()): SubscriptionFormModel {
  return {
    id: null,
    agentType: null,
    planId: null,
    enterpriseId: null,
    startsAt: now,
    accountEmail: "",
    credential: "",
    remark: "",
  };
}

export function subscriptionToForm(s: AdminSubscriptionResponse): SubscriptionFormModel {
  return {
    id: s.id,
    agentType: s.agentType,
    planId: s.planId,
    enterpriseId: s.enterpriseId,
    // 服务端给的是带 Z 的 UTC 串，解析成 Date 后由控件按本地时区回显
    startsAt: new Date(s.startsAt),
    accountEmail: s.accountEmail ?? "",
    // 服务端不回传凭据，回填一律为空；提交时空串即表示不修改
    credential: "",
    remark: s.remark ?? "",
  };
}

/**
 * @param enterpriseDomain 所选归属企业的域名；null = 个人订阅，不校验账号邮箱域名。
 *        由调用方按 form.enterpriseId 从企业列表里查出来传入，本函数不碰企业数据。
 */
export function validateSubscriptionForm(
  form: SubscriptionFormModel,
  mode: "create" | "edit",
  enterpriseDomain: string | null = null,
): string[] {
  const errors: string[] = [];
  if (mode === "create" && form.agentType === null) {
    errors.push("请选择 Agent 类型");
  }
  if (mode === "create" && form.planId === null) {
    errors.push("请选择套餐");
  }
  if (!form.startsAt) {
    errors.push("起期不能为空");
  }
  // 账号邮箱选填：留空跳过；填了先看格式，格式对了再看是不是落在企业域名下
  const accountEmail = normalizeEmail(form.accountEmail);
  if (accountEmail !== "") {
    if (!EMAIL_PATTERN.test(accountEmail)) {
      errors.push("账号邮箱格式不正确");
    } else if (enterpriseDomain !== null && domainOf(accountEmail) !== enterpriseDomain.toLowerCase()) {
      errors.push(`账号邮箱域名须与企业域名 ${enterpriseDomain} 一致`);
    }
  }
  return errors;
}

/** 账号邮箱统一去空白 + 转小写：与服务端入库形态一致，比对域名也才靠谱 */
function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

export function buildSubscriptionCreatePayload(form: SubscriptionFormModel): SubscriptionCreateRequest {
  if (form.planId === null || !form.startsAt) {
    // 调用契约：先过 validateSubscriptionForm；走到这里是编程错误
    throw new Error("套餐或起期未填，先通过表单校验再构造入参");
  }
  return {
    planId: form.planId,
    enterpriseId: form.enterpriseId,
    // toISOString 天然输出 UTC（带 Z），提交即绝对时刻
    startsAt: form.startsAt.toISOString(),
    accountEmail: normalizeEmail(form.accountEmail),
    // 空串 = 沿用原值，服务端按空白转 null 处理
    credential: form.credential.trim(),
    remark: form.remark.trim(),
  };
}

export function buildSubscriptionUpdatePayload(form: SubscriptionFormModel): SubscriptionUpdateRequest {
  if (!form.startsAt) {
    throw new Error("起期未填，先通过表单校验再构造入参");
  }
  return {
    enterpriseId: form.enterpriseId,
    startsAt: form.startsAt.toISOString(),
    accountEmail: normalizeEmail(form.accountEmail),
    credential: form.credential.trim(),
    remark: form.remark.trim(),
  };
}

/** 止期推算：起期 + 套餐天数。按绝对毫秒加（1 天 = 24h），与服务端 Instant.plus 完全一致 */
export function computeEndsAt(startsAt: Date, durationDays: number): Date {
  return new Date(startsAt.getTime() + durationDays * 24 * 60 * 60 * 1000);
}

/** 套餐下拉的展示标签：名称 + 时长 + 价格。agent 类型已由上一级选择承担，不再混入 */
export function formatPlanLabel(plan: PlanResponse): string {
  return `${plan.name}（${plan.durationDays} 天 · ${plan.price} ${plan.currency}）`;
}

/**
 * Agent 类型下拉选项：只取「有上架套餐」的类型（选了也没套餐可挑的类型不列），
 * 按首次出现顺序去重；未知类型直接展示原始取值。
 */
export function agentTypeOptions(plans: PlanResponse[]): Array<{ value: string; label: string }> {
  const seen = new Set<string>();
  const options: Array<{ value: string; label: string }> = [];
  for (const plan of plans) {
    if (!plan.enabled || seen.has(plan.agentType)) {
      continue;
    }
    seen.add(plan.agentType);
    options.push({
      value: plan.agentType,
      label: AGENT_TYPE_LABELS[plan.agentType as keyof typeof AGENT_TYPE_LABELS] ?? plan.agentType,
    });
  }
  return options;
}

/** 套餐下拉选项：只列所选 agent 类型下的上架套餐；未选类型时为空 */
export function planOptionsForAgent(
  plans: PlanResponse[],
  agentType: string | null,
): Array<{ value: number; label: string }> {
  if (agentType === null) {
    return [];
  }
  return plans
    .filter((plan) => plan.enabled && plan.agentType === agentType)
    .map((plan) => ({ value: plan.id, label: formatPlanLabel(plan) }));
}

/**
 * 归属企业下拉选项：只列启用中、且支持所选 agent 类型的企业；未选类型时为空。
 * 与套餐下拉同一条链路——先定 agent 类型，套餐与企业再各自按它收窄。
 */
export function enterpriseOptionsForAgent(
  enterprises: EnterpriseResponse[],
  agentType: string | null,
): Array<{ value: number; label: string }> {
  if (agentType === null) {
    return [];
  }
  return enterprises
    .filter((e) => e.enabled && e.agentTypes.includes(agentType))
    .map((e) => ({ value: e.id, label: `${e.name}（${e.domain}）` }));
}
