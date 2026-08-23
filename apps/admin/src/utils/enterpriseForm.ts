import type { EnterpriseResponse, EnterpriseSaveRequest } from "../api/types";

/** 裸域名：不带协议、不带路径，至少一个点，每段以字母数字开头结尾。大小写都收，提交前统一转小写 */
const DOMAIN_PATTERN =
  /^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$/;

/** 企业表单模型。agentTypes 用 string[]：回填未知类型时保留原值，避免打开即误改 */
export interface EnterpriseFormModel {
  name: string;
  domain: string;
  agentTypes: string[];
  enabled: boolean;
  remark: string;
}

export function emptyEnterpriseForm(): EnterpriseFormModel {
  return {
    name: "",
    domain: "",
    agentTypes: [],
    enabled: true,
    remark: "",
  };
}

export function enterpriseToForm(enterprise: EnterpriseResponse): EnterpriseFormModel {
  return {
    name: enterprise.name,
    domain: enterprise.domain,
    // 复制一份：表单里的勾选不该改到列表数据上
    agentTypes: [...enterprise.agentTypes],
    enabled: enterprise.enabled,
    remark: enterprise.remark ?? "",
  };
}

export function validateEnterpriseForm(form: EnterpriseFormModel): string[] {
  const errors: string[] = [];
  if (!form.name.trim()) {
    errors.push("企业名称不能为空");
  }
  const domain = form.domain.trim();
  if (!domain) {
    errors.push("企业域名不能为空");
  } else if (!DOMAIN_PATTERN.test(domain)) {
    errors.push("企业域名格式不对，形如 acme.com");
  }
  if (form.agentTypes.length === 0) {
    errors.push("请至少选择一个 Agent 类型");
  }
  return errors;
}

export function buildEnterprisePayload(form: EnterpriseFormModel): EnterpriseSaveRequest {
  return {
    name: form.name.trim(),
    // 服务端也会转小写，这里同样处理是为了让提交体与最终落库形态一致
    domain: form.domain.trim().toLowerCase(),
    agentTypes: form.agentTypes as EnterpriseSaveRequest["agentTypes"],
    enabled: form.enabled,
    remark: form.remark.trim(),
  };
}

/** 复选框的勾选/取消：已选则移除，未选则追加 */
export function toggleAgentType(form: EnterpriseFormModel, agentType: string): void {
  const index = form.agentTypes.indexOf(agentType);
  if (index >= 0) {
    form.agentTypes.splice(index, 1);
  } else {
    form.agentTypes.push(agentType);
  }
}
