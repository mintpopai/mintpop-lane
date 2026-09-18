import { describe, expect, it } from "vitest";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import {
  buildUserPayload,
  FRONT_SELECTION,
  frontSelectionToPayload,
  selectableFrontNodes,
  selectableLandNodes,
  userToForm,
} from "./userForm";

function makeNode(overrides: Partial<AdminNodeResponse>): AdminNodeResponse {
  return {
    id: 1,
    name: "US-01",
    role: "FRONT",
    protocol: "TROJAN",
    serverAddr: "us.example.com",
    port: 443,
    extraConfig: {},
    egressIp: null,
    egressTimezone: null,
    status: "ENABLED",
    remark: null,
    secretConfigured: true,
    capacity: null,
    assignedUserCount: null,
    groupId: null,
    groupName: null,
    sourceType: null,
    failureDomain: null,
    createdAt: "2026-08-18T10:00:00",
    updatedAt: "2026-08-18T10:00:00",
    ...overrides,
  };
}

function makeUser(overrides: Partial<AdminUserResponse> = {}): AdminUserResponse {
  return {
    id: 5,
    subject: "logto-user-1",
    email: "zhangsan@example.com",
    role: "MEMBER",
    status: "ACTIVE",
    frontNodeId: 1,
    frontNodeName: "US-01",
    frontNodes: [],
    failureDomainCount: 0,
    landNodeId: 11,
    landNodeName: "LAND-东京-03",
    egressIp: "1.2.3.4",
    activeSubscriptions: [],
    remark: null,
    createdAt: "2026-08-18T10:00:00",
    updatedAt: "2026-08-18T10:00:00",
    ...overrides,
  };
}

describe("buildUserPayload", () => {
  it("未选落地/第一跳节点提交 null，而不是 0", () => {
    const payload = buildUserPayload({
      id: 5,
      status: "ACTIVE",
      frontNodeId: null,
      frontAction: "KEEP",
      landNodeId: null,
      remark: "",
    });

    expect(payload.frontNodeId).toBeNull();
    expect(payload.landNodeId).toBeNull();
  });

  it("选了节点就原样直通两个节点 id", () => {
    const payload = buildUserPayload({
      id: 5,
      status: "ACTIVE",
      frontNodeId: 3,
      frontAction: "KEEP",
      landNodeId: 11,
      remark: "",
    });

    expect(payload.frontNodeId).toBe(3);
    expect(payload.landNodeId).toBe(11);
  });

  it("清空下拉产出的 undefined 也收成 null——接口上「未分配」只有一种表示", () => {
    const payload = buildUserPayload({
      id: 5,
      status: "ACTIVE",
      // el-select clearable 清空后 v-model 拿到的是 undefined，类型上仍标成 null
      frontNodeId: undefined as unknown as null,
      frontAction: "KEEP",
      landNodeId: undefined as unknown as null,
      remark: "",
    });

    expect(payload.frontNodeId).toBeNull();
    expect(payload.landNodeId).toBeNull();
  });

  it("备注去掉首尾空白后带上", () => {
    const payload = buildUserPayload({
      id: 5,
      status: "ACTIVE",
      frontNodeId: null,
      frontAction: "KEEP",
      landNodeId: null,
      remark: "  老客户，续费谈过  ",
    });

    expect(payload.remark).toBe("老客户，续费谈过");
  });

  it("只敲了空格的备注等于没写，提交空串", () => {
    const payload = buildUserPayload({
      id: 5,
      status: "ACTIVE",
      frontNodeId: null,
      frontAction: "KEEP",
      landNodeId: null,
      remark: "   ",
    });

    expect(payload.remark).toBe("");
  });

  it("状态原样带上", () => {
    const payload = buildUserPayload({
      id: 5,
      status: "SUSPENDED",
      frontNodeId: null,
      frontAction: "KEEP",
      landNodeId: null,
      remark: "",
    });

    expect(payload.status).toBe("SUSPENDED");
  });
});

describe("selectableFrontNodes", () => {
  it("只留启用的第一跳节点——禁用节点选了也下发不了", () => {
    const nodes = [
      makeNode({ id: 1, role: "FRONT", status: "ENABLED" }),
      makeNode({ id: 2, role: "FRONT", status: "DISABLED" }),
      makeNode({ id: 3, role: "LAND", status: "ENABLED" }),
    ];

    expect(selectableFrontNodes(nodes).map((n) => n.id)).toEqual([1]);
  });
});

describe("selectableLandNodes", () => {
  it("容量已满的落地节点不可选，还有余量的可选（哪怕已有人绑定）", () => {
    const nodes = [
      makeNode({ id: 10, role: "LAND", capacity: 10, assignedUserCount: 3 }),
      makeNode({ id: 11, role: "LAND", capacity: 2, assignedUserCount: 2 }),
    ];

    expect(selectableLandNodes(nodes, null).map((n) => n.id)).toEqual([10]);
  });

  it("自己当前绑着的那个仍可选（即使已满），否则编辑自己时选项会凭空消失", () => {
    const nodes = [
      makeNode({ id: 10, role: "LAND", capacity: 10, assignedUserCount: 0 }),
      makeNode({ id: 11, role: "LAND", capacity: 1, assignedUserCount: 1 }),
    ];

    expect(selectableLandNodes(nodes, 11).map((n) => n.id)).toEqual([10, 11]);
  });

  it("禁用的落地节点不可选", () => {
    const nodes = [
      makeNode({ id: 12, role: "LAND", status: "DISABLED", capacity: 10, assignedUserCount: 0 }),
    ];

    expect(selectableLandNodes(nodes, null)).toEqual([]);
  });
});

describe("userToForm", () => {
  it("按新模型逐字段回填：第一跳一律是 KEEP，且 frontNodeId 回填成 null", () => {
    const form = userToForm(makeUser());

    expect(form).toEqual({
      id: 5,
      status: "ACTIVE",
      frontAction: "KEEP",
      frontNodeId: null,
      landNodeId: 11,
      remark: "",
    });
  });

  it("服务端没写备注时回填成空串，输入框拿到的不是 null", () => {
    expect(userToForm(makeUser({ remark: null })).remark).toBe("");
  });

  it("有备注就原样回填", () => {
    expect(userToForm(makeUser({ remark: "试用期" })).remark).toBe("试用期");
  });

  it("回填永远是 KEEP——改备注、改状态这些保存不该顺手动别人的前置组", () => {
    expect(userToForm(makeUser({ frontNodeId: 7 })).frontAction).toBe("KEEP");
  });

  it("回填不把用户当前的 frontNodeId 带出来：KEEP 下服务端忽略它，带着一个过期快照只会误导下一个人", () => {
    expect(userToForm(makeUser({ frontNodeId: 7 })).frontNodeId).toBeNull();
  });
});

describe("frontSelectionToPayload", () => {
  it("选「自动分配」发 AUTO：请服务端按故障域重算一组，节点 id 不再有意义", () => {
    expect(frontSelectionToPayload(FRONT_SELECTION.AUTO_ALLOCATE)).toEqual({
      frontAction: "AUTO",
      frontNodeId: null,
    });
  });

  it("选「不分配」发 CLEAR：字面意思的清空，而不是暗示自动分配", () => {
    expect(frontSelectionToPayload(null)).toEqual({ frontAction: "CLEAR", frontNodeId: null });
  });

  it("选具体节点发 PIN：钉死到这一个（运维逃生口）", () => {
    expect(frontSelectionToPayload(9)).toEqual({ frontAction: "PIN", frontNodeId: 9 });
  });
});
