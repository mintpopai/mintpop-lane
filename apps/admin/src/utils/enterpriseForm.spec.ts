import { describe, expect, it } from "vitest";
import type { EnterpriseResponse } from "../api/types";
import {
  buildEnterprisePayload,
  emptyEnterpriseForm,
  enterpriseToForm,
  toggleAgentType,
  validateEnterpriseForm,
} from "./enterpriseForm";

const enterprise: EnterpriseResponse = {
  id: 7,
  name: "Acme 科技",
  domain: "acme.com",
  agentTypes: ["CLAUDE", "CODEX"],
  enabled: true,
  remark: "首批客户",
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

describe("emptyEnterpriseForm", () => {
  it("默认启用、不预选 agent 类型、其余留白", () => {
    expect(emptyEnterpriseForm()).toEqual({
      name: "",
      domain: "",
      agentTypes: [],
      enabled: true,
      remark: "",
    });
  });
});

describe("enterpriseToForm / buildEnterprisePayload", () => {
  it("企业记录转表单再转回提交体，字段原样往返", () => {
    const form = enterpriseToForm(enterprise);
    expect(form.name).toBe("Acme 科技");
    expect(form.domain).toBe("acme.com");
    expect(form.agentTypes).toEqual(["CLAUDE", "CODEX"]);

    expect(buildEnterprisePayload(form)).toEqual({
      name: "Acme 科技",
      domain: "acme.com",
      agentTypes: ["CLAUDE", "CODEX"],
      enabled: true,
      remark: "首批客户",
    });
  });

  it("remark 为 null 的记录转表单后是空串；提交体去除首尾空白并把域名转小写", () => {
    const form = enterpriseToForm({ ...enterprise, remark: null });
    expect(form.remark).toBe("");

    form.name = "  Acme 科技  ";
    form.domain = "  ACME.Com  ";
    form.remark = " 备注 ";
    const payload = buildEnterprisePayload(form);
    expect(payload.name).toBe("Acme 科技");
    expect(payload.domain).toBe("acme.com");
    expect(payload.remark).toBe("备注");
  });

  it("表单里的 agentTypes 是新数组，改它不会污染原记录", () => {
    const form = enterpriseToForm(enterprise);
    form.agentTypes.push("FUTURE_AGENT");
    expect(enterprise.agentTypes).toEqual(["CLAUDE", "CODEX"]);
  });
});

describe("validateEnterpriseForm", () => {
  it("合法表单无错误", () => {
    expect(validateEnterpriseForm(enterpriseToForm(enterprise))).toEqual([]);
  });

  it("名称必填", () => {
    const form = enterpriseToForm(enterprise);
    form.name = "  ";
    expect(validateEnterpriseForm(form)).toContain("企业名称不能为空");
  });

  it("域名必填", () => {
    const form = enterpriseToForm(enterprise);
    form.domain = "  ";
    expect(validateEnterpriseForm(form)).toContain("企业域名不能为空");
  });

  it("域名要是形如 acme.com 的裸域名，带协议或路径都拦下", () => {
    const form = enterpriseToForm(enterprise);
    for (const bad of ["https://acme.com", "acme.com/path", "acme", "acme..com", "-acme.com"]) {
      form.domain = bad;
      expect(validateEnterpriseForm(form)).toContain("企业域名格式不对，形如 acme.com");
    }
  });

  it("大写域名合法：提交前会统一转小写", () => {
    const form = enterpriseToForm(enterprise);
    form.domain = "ACME.COM";
    expect(validateEnterpriseForm(form)).toEqual([]);
  });

  it("agent 类型至少选一个", () => {
    const form = enterpriseToForm(enterprise);
    form.agentTypes = [];
    expect(validateEnterpriseForm(form)).toContain("请至少选择一个 Agent 类型");
  });
});

describe("toggleAgentType", () => {
  it("未选中则加入，已选中则移除", () => {
    const form = emptyEnterpriseForm();
    toggleAgentType(form, "CLAUDE");
    expect(form.agentTypes).toEqual(["CLAUDE"]);
    toggleAgentType(form, "CODEX");
    expect(form.agentTypes).toEqual(["CLAUDE", "CODEX"]);
    toggleAgentType(form, "CLAUDE");
    expect(form.agentTypes).toEqual(["CODEX"]);
  });
});
