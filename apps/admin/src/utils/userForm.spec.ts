import { describe, expect, it } from "vitest";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import { buildUserPayload, selectableFrontNodes, selectableLandNodes, userToForm } from "./userForm";

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

function 用户(overrides: Partial<AdminUserResponse> = {}): AdminUserResponse {
  return {
    id: 5,
    subject: "logto-user-1",
    email: "zhangsan@example.com",
    name: "张三",
    role: "MEMBER",
    status: "ACTIVE",
    frontNodeId: 1,
    frontNodeName: "US-01",
    landNodeId: 11,
    landNodeName: "LAND-东京-03",
    egressIps: ["1.2.3.4"],
    activeSubscriptions: [],
    createdAt: "2026-08-18T10:00:00",
    updatedAt: "2026-08-18T10:00:00",
    ...overrides,
  };
}

describe("buildUserPayload", () => {
  it("未选落地/第一跳节点提交 null，而不是 0", () => {
    const payload = buildUserPayload({ id: 5, status: "ACTIVE", frontNodeId: null, landNodeId: null });

    expect(payload.frontNodeId).toBeNull();
    expect(payload.landNodeId).toBeNull();
  });

  it("选了节点就原样直通两个节点 id", () => {
    const payload = buildUserPayload({ id: 5, status: "ACTIVE", frontNodeId: 3, landNodeId: 11 });

    expect(payload.frontNodeId).toBe(3);
    expect(payload.landNodeId).toBe(11);
  });

  it("清空下拉产出的 undefined 也收成 null——接口上「未分配」只有一种表示", () => {
    const payload = buildUserPayload({
      id: 5,
      status: "ACTIVE",
      // el-select clearable 清空后 v-model 拿到的是 undefined，类型上仍标成 null
      frontNodeId: undefined as unknown as null,
      landNodeId: undefined as unknown as null,
    });

    expect(payload.frontNodeId).toBeNull();
    expect(payload.landNodeId).toBeNull();
  });

  it("状态原样带上", () => {
    const payload = buildUserPayload({ id: 5, status: "SUSPENDED", frontNodeId: null, landNodeId: null });

    expect(payload.status).toBe("SUSPENDED");
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
  it("按新模型逐字段回填", () => {
    const form = userToForm(用户());

    expect(form).toEqual({
      id: 5,
      status: "ACTIVE",
      frontNodeId: 1,
      landNodeId: 11,
    });
  });
});
