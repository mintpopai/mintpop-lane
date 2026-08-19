import { describe, expect, it } from "vitest";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import {
  buildUserPayload,
  emptyUserForm,
  selectableFrontNodes,
  selectableLandNodes,
  userToForm,
  validateUserForm,
  type UserFormModel,
} from "./userForm";

function 表单(overrides: Partial<UserFormModel> = {}): UserFormModel {
  return {
    ...emptyUserForm(),
    subject: "logto-user-1",
    name: "张三",
    frontNodeId: 1,
    ...overrides,
  };
}

function 节点(overrides: Partial<AdminNodeResponse>): AdminNodeResponse {
  return {
    id: 1,
    name: "US-01",
    role: "FRONT",
    protocol: "TROJAN",
    serverAddr: "us.example.com",
    port: 443,
    extraConfig: {},
    egressIps: [],
    status: "ENABLED",
    remark: null,
    secretConfigured: true,
    assignedUserName: null,
    createdAt: "2026-08-18T10:00:00",
    updatedAt: "2026-08-18T10:00:00",
    ...overrides,
  };
}

describe("buildUserPayload", () => {
  it("凭据留空时提交空串——服务端据此沿用原凭据，绝不因为没重填就被清掉", () => {
    expect(buildUserPayload(表单({ claudeCredential: "   " })).claudeCredential).toBe("");
  });

  it("凭据填了就整条覆盖，并去掉首尾空白（粘贴时最容易多带）", () => {
    expect(buildUserPayload(表单({ claudeCredential: " sk-ant-oat01-xxx \n" })).claudeCredential).toBe(
      "sk-ant-oat01-xxx",
    );
  });

  it("未选落地节点提交 null，而不是 0 或空串", () => {
    expect(buildUserPayload(表单({ landNodeId: null })).landNodeId).toBeNull();
  });

  it("姓名与 subject 去首尾空白后提交", () => {
    const payload = buildUserPayload(表单({ subject: " logto-user-1 ", name: " 张三 " }));

    expect(payload.subject).toBe("logto-user-1");
    expect(payload.name).toBe("张三");
  });

  it("状态原样带上", () => {
    expect(buildUserPayload(表单({ status: "SUSPENDED" })).status).toBe("SUSPENDED");
  });
});

describe("validateUserForm", () => {
  it("合法表单没有错误", () => {
    expect(validateUserForm(表单())).toEqual([]);
  });

  it("必填项缺失逐条点名", () => {
    const errors = validateUserForm(表单({ subject: "", name: "", frontNodeId: null }));

    expect(errors).toContain("Logto user id 不能为空");
    expect(errors).toContain("姓名不能为空");
    expect(errors).toContain("必须选择第一跳节点");
  });
});

describe("selectableFrontNodes", () => {
  it("只留启用的第一跳节点——禁用节点选了也下发不了", () => {
    const nodes = [
      节点({ id: 1, role: "FRONT", status: "ENABLED" }),
      节点({ id: 2, role: "FRONT", status: "DISABLED" }),
      节点({ id: 3, role: "LAND", status: "ENABLED" }),
    ];

    expect(selectableFrontNodes(nodes).map((n) => n.id)).toEqual([1]);
  });
});

describe("selectableLandNodes", () => {
  it("已被别人占用的落地节点不可选——一人一个出口是硬约束", () => {
    const nodes = [
      节点({ id: 10, role: "LAND", assignedUserName: null }),
      节点({ id: 11, role: "LAND", assignedUserName: "李四" }),
    ];

    expect(selectableLandNodes(nodes, null).map((n) => n.id)).toEqual([10]);
  });

  it("自己当前占着的那个仍可选，否则编辑自己时选项会凭空消失", () => {
    const nodes = [
      节点({ id: 10, role: "LAND", assignedUserName: null }),
      节点({ id: 11, role: "LAND", assignedUserName: "张三" }),
    ];

    expect(selectableLandNodes(nodes, 11).map((n) => n.id)).toEqual([10, 11]);
  });

  it("禁用的落地节点不可选", () => {
    const nodes = [节点({ id: 12, role: "LAND", status: "DISABLED", assignedUserName: null })];

    expect(selectableLandNodes(nodes, null)).toEqual([]);
  });
});

describe("userToForm", () => {
  it("回填时凭据一定是空的——服务端本就不回传，留空即不修改", () => {
    const user: AdminUserResponse = {
      id: 5,
      subject: "logto-user-1",
      name: "张三",
      role: "MEMBER",
      status: "ACTIVE",
      frontNodeId: 1,
      frontNodeName: "US-01",
      landNodeId: 11,
      landNodeName: "LAND-东京-03",
      egressIps: ["1.2.3.4"],
      credentialConfigured: true,
      createdAt: "2026-08-18T10:00:00",
      updatedAt: "2026-08-18T10:00:00",
    };

    const form = userToForm(user);

    expect(form.claudeCredential).toBe("");
    expect(form.landNodeId).toBe(11);
  });
});
