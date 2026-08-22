import { describe, expect, it } from "vitest";
import {
  applyProtocol,
  buildNodePayload,
  emptyNodeForm,
  isIpLiteral,
  nodeToForm,
  parseScalar,
  syncEgressIpFromServerAddr,
  syncEgressTimezoneFromLookup,
  validateNodeForm,
  PROTOCOL_SECRET_KEYS,
  type NodeFormModel,
} from "./nodeForm";
import type { AdminNodeResponse } from "../api/types";

function makeForm(overrides: Partial<NodeFormModel> = {}): NodeFormModel {
  return {
    ...emptyNodeForm("LAND"),
    name: "LAND-东京-03",
    serverAddr: "tokyo.example.com",
    port: 443,
    ...overrides,
  };
}

describe("parseScalar", () => {
  it("把 true/false 还原成布尔——mihomo 的 skip-cert-verify 要的是布尔不是字符串", () => {
    expect(parseScalar("true")).toBe(true);
    expect(parseScalar("false")).toBe(false);
  });

  it("把纯数字还原成数字", () => {
    expect(parseScalar("8443")).toBe(8443);
  });

  it("其余原样当字符串，含前导零的编号不会被吃掉", () => {
    expect(parseScalar("example.com")).toBe("example.com");
    expect(parseScalar("007")).toBe("007");
  });
});

describe("validateNodeForm", () => {
  it("合法表单没有错误", () => {
    expect(validateNodeForm(makeForm())).toEqual([]);
  });

  it("敏感键混进透传键要当场拦下——服务端会回 110001，但那时用户已经不知道错在哪了", () => {
    const form = makeForm({
      protocol: "TROJAN",
      extraConfig: [{ key: "password", value: "偷偷写在这里" }],
    });

    expect(validateNodeForm(form)).toContain("password 属于该协议的敏感键，必须填在「敏感配置」里，不能放进透传键");
  });

  it("透传键重复要拦下，否则后一条会静默覆盖前一条", () => {
    const form = makeForm({
      extraConfig: [
        { key: "sni", value: "a.com" },
        { key: "sni", value: "b.com" },
      ],
    });

    expect(validateNodeForm(form)).toContain("透传键 sni 重复");
  });

  it("必填项缺失逐条点名", () => {
    const form = makeForm({ name: "", serverAddr: "", port: null });
    const errors = validateNodeForm(form);

    expect(errors).toContain("节点名不能为空");
    expect(errors).toContain("节点地址不能为空");
    expect(errors).toContain("端口必须在 1 到 65535 之间");
  });

  it("端口越界也拦", () => {
    expect(validateNodeForm(makeForm({ port: 70000 }))).toContain("端口必须在 1 到 65535 之间");
  });

  it("编辑时切换了协议、敏感键却留空要拦下——服务端会把留空当成沿用旧协议的密钥", () => {
    const form = makeForm({ originalProtocol: "TROJAN", protocol: "VMESS", secret: { uuid: "" } });

    expect(validateNodeForm(form)).toContain("切换协议后必须重新填写敏感配置：uuid");
  });

  it("切换协议并重填了敏感键就放行", () => {
    const form = makeForm({ originalProtocol: "TROJAN", protocol: "VMESS", secret: { uuid: "新的uuid" } });

    expect(validateNodeForm(form)).toEqual([]);
  });

  it("新建时没有原协议，敏感键留空仍然允许——先建节点、随后补密码是正常流程", () => {
    const form = makeForm({ originalProtocol: null, protocol: "VMESS", secret: { uuid: "" } });

    expect(validateNodeForm(form)).toEqual([]);
  });

  it("出口 IP 填了就必须是合法的 IP 字面量——它要与客户端探测到的出口 IP 逐字比对", () => {
    expect(validateNodeForm(makeForm({ egressIp: "1.2.3.4 5.6.7.8" }))).toContain(
      "出口 IP「1.2.3.4 5.6.7.8」不是合法的 IP 地址",
    );
    expect(validateNodeForm(makeForm({ egressIp: "tokyo.example.com" }))).toContain(
      "出口 IP「tokyo.example.com」不是合法的 IP 地址",
    );
  });

  it("出口 IP 留空允许——先建节点、随后补出口是正常流程", () => {
    expect(validateNodeForm(makeForm({ egressIp: "" }))).toEqual([]);
  });

  it("IPv4 与 IPv6 字面量都放行", () => {
    expect(validateNodeForm(makeForm({ egressIp: "203.0.113.10" }))).toEqual([]);
    expect(validateNodeForm(makeForm({ egressIp: "2001:db8::1" }))).toEqual([]);
  });

  it("出口时区填了就必须是合法的 IANA 时区名——坏值会被服务端 410015 挡回来，这里先给能看懂的话", () => {
    expect(validateNodeForm(makeForm({ egressTimezone: "东京时间" }))).toContain(
      "出口时区「东京时间」不是合法的 IANA 时区名",
    );
  });

  it("出口时区留空或填合法时区名都放行", () => {
    expect(validateNodeForm(makeForm({ egressTimezone: "" }))).toEqual([]);
    expect(validateNodeForm(makeForm({ egressTimezone: "Asia/Tokyo" }))).toEqual([]);
  });
});

describe("isIpLiteral", () => {
  it("识别合法 IPv4", () => {
    expect(isIpLiteral("1.2.3.4")).toBe(true);
    expect(isIpLiteral("255.255.255.255")).toBe(true);
  });

  it("拒绝越界、前导零与域名", () => {
    expect(isIpLiteral("256.1.1.1")).toBe(false);
    expect(isIpLiteral("01.2.3.4")).toBe(false);
    expect(isIpLiteral("tokyo.example.com")).toBe(false);
    expect(isIpLiteral("1.2.3.4:443")).toBe(false);
    expect(isIpLiteral("")).toBe(false);
  });

  it("识别合法 IPv6", () => {
    expect(isIpLiteral("2001:db8::1")).toBe(true);
    expect(isIpLiteral("::1")).toBe(true);
    expect(isIpLiteral("2001:0db8:0000:0000:0000:0000:0000:0001")).toBe(true);
  });

  it("拒绝形似 IPv6 的坏值", () => {
    expect(isIpLiteral("host:443")).toBe(false);
    expect(isIpLiteral("1::2::3")).toBe(false);
    expect(isIpLiteral("2001:db8::12345")).toBe(false);
  });
});

describe("syncEgressIpFromServerAddr", () => {
  it("落地节点地址填成 IP 字面量时，空的出口 IP 预填为该地址", () => {
    const form = makeForm({ serverAddr: "203.0.113.10", egressIp: "" });

    expect(syncEgressIpFromServerAddr(form, "203.0.113.1").egressIp).toBe("203.0.113.10");
  });

  it("出口 IP 若还等于上一次的地址值（即此前是预填的），跟随地址一起更新", () => {
    const form = makeForm({ serverAddr: "203.0.113.20", egressIp: "203.0.113.10" });

    expect(syncEgressIpFromServerAddr(form, "203.0.113.10").egressIp).toBe("203.0.113.20");
  });

  it("出口 IP 已被手工改过（与上一次地址不同）就不动它", () => {
    const form = makeForm({ serverAddr: "203.0.113.20", egressIp: "198.51.100.7" });

    expect(syncEgressIpFromServerAddr(form, "203.0.113.10").egressIp).toBe("198.51.100.7");
  });

  it("地址是域名时不预填", () => {
    const form = makeForm({ serverAddr: "tokyo.example.com", egressIp: "" });

    expect(syncEgressIpFromServerAddr(form, "").egressIp).toBe("");
  });

  it("第一跳节点不预填——出口 IP 是落地节点的属性", () => {
    const form = makeForm({ role: "FRONT", serverAddr: "203.0.113.10", egressIp: "" });

    expect(syncEgressIpFromServerAddr(form, "").egressIp).toBe("");
  });
});

describe("syncEgressTimezoneFromLookup", () => {
  it("落地节点时区为空时，用查询结果预填", () => {
    const form = makeForm({ egressTimezone: "" });

    expect(syncEgressTimezoneFromLookup(form, "Asia/Tokyo", "").egressTimezone).toBe("Asia/Tokyo");
  });

  it("时区若还等于上一次的预填值，跟随新查询结果更新", () => {
    const form = makeForm({ egressTimezone: "Asia/Tokyo" });

    expect(syncEgressTimezoneFromLookup(form, "America/Los_Angeles", "Asia/Tokyo").egressTimezone).toBe(
      "America/Los_Angeles",
    );
  });

  it("时区已被手工改过（与上一次预填值不同）就不动它", () => {
    const form = makeForm({ egressTimezone: "Asia/Shanghai" });

    expect(syncEgressTimezoneFromLookup(form, "America/Los_Angeles", "Asia/Tokyo").egressTimezone).toBe(
      "Asia/Shanghai",
    );
  });

  it("第一跳节点不预填——出口时区是落地节点的属性", () => {
    const form = makeForm({ role: "FRONT", egressTimezone: "" });

    expect(syncEgressTimezoneFromLookup(form, "Asia/Tokyo", "").egressTimezone).toBe("");
  });

  it("查询结果为空（GeoIP 查不到）时不动表单", () => {
    const form = makeForm({ egressTimezone: "" });

    expect(syncEgressTimezoneFromLookup(form, null, "").egressTimezone).toBe("");
  });
});

describe("applyProtocol", () => {
  it("切协议时把敏感键整组换成新协议的那一组", () => {
    const form = applyProtocol(makeForm({ protocol: "TROJAN" }), "VMESS");

    expect(Object.keys(form.secret)).toEqual(["uuid"]);
  });

  it("已填的透传键保留，空行按新协议的常用键重铺", () => {
    const form = applyProtocol(
      makeForm({
        protocol: "TROJAN",
        extraConfig: [
          { key: "sni", value: "a.com" },
          { key: "skip-cert-verify", value: "" },
        ],
      }),
      "VMESS",
    );

    expect(form.extraConfig[0]).toEqual({ key: "sni", value: "a.com" });
    expect(form.extraConfig.slice(1).map((row) => row.key)).toEqual(["alterId", "cipher", "network"]);
  });

  it("不动 originalProtocol——它记的是库里那条记录的协议，正是判断要不要重填敏感键的依据", () => {
    const form = applyProtocol(makeForm({ originalProtocol: "TROJAN", protocol: "TROJAN" }), "VMESS");

    expect(form.originalProtocol).toBe("TROJAN");
  });
});

describe("buildNodePayload", () => {
  it("敏感键全部留空时提交空对象——服务端据此沿用原密码，不会被清掉", () => {
    const form = makeForm({ protocol: "TROJAN", secret: { password: "" } });

    expect(buildNodePayload(form).secret).toEqual({});
  });

  it("敏感键填了才提交，且只提交填了的那些", () => {
    const form = makeForm({ protocol: "SOCKS5", secret: { username: "lane", password: "" } });

    expect(buildNodePayload(form).secret).toEqual({ username: "lane" });
  });

  it("透传键按标量还原类型后提交，空 key 的行丢弃", () => {
    const form = makeForm({
      extraConfig: [
        { key: "sni", value: "tokyo.example.com" },
        { key: "skip-cert-verify", value: "true" },
        { key: "", value: "这行是用户点了新增又没填" },
      ],
    });

    expect(buildNodePayload(form).extraConfig).toEqual({
      sni: "tokyo.example.com",
      "skip-cert-verify": true,
    });
  });

  it("只填了键没填值的行也丢弃——新建表单默认铺了几行常用键的空行，不能把 sni:\"\" 下发给 mihomo", () => {
    const form = makeForm({
      extraConfig: [
        { key: "sni", value: "" },
        { key: "skip-cert-verify", value: "  " },
      ],
    });

    expect(buildNodePayload(form).extraConfig).toEqual({});
  });

  it("落地节点的出口 IP 去掉首尾空白后提交，留空提交 null", () => {
    expect(buildNodePayload(makeForm({ egressIp: " 1.2.3.4 " })).egressIp).toBe("1.2.3.4");
    expect(buildNodePayload(makeForm({ egressIp: "  " })).egressIp).toBeNull();
  });

  it("第一跳节点不提交出口 IP——出口 IP 是落地节点的属性", () => {
    const form = makeForm({ role: "FRONT", egressIp: "1.2.3.4" });

    expect(buildNodePayload(form).egressIp).toBeNull();
  });

  it("落地节点的出口时区去掉首尾空白后提交，留空提交 null", () => {
    expect(buildNodePayload(makeForm({ egressTimezone: " Asia/Tokyo " })).egressTimezone).toBe("Asia/Tokyo");
    expect(buildNodePayload(makeForm({ egressTimezone: "  " })).egressTimezone).toBeNull();
  });

  it("第一跳节点不提交出口时区——出口时区是落地节点的属性", () => {
    const form = makeForm({ role: "FRONT", egressTimezone: "Asia/Tokyo" });

    expect(buildNodePayload(form).egressTimezone).toBeNull();
  });

  it("备注为空时提交空串而不是 undefined，避免 JSON 里整个键消失", () => {
    expect(buildNodePayload(makeForm()).remark).toBe("");
  });
});

describe("nodeToForm", () => {
  it("回填时不带任何密码——服务端本就不回传，表单里永远是空的待填状态", () => {
    const node: AdminNodeResponse = {
      id: 3,
      name: "LAND-东京-03",
      role: "LAND",
      protocol: "TROJAN",
      serverAddr: "tokyo.example.com",
      port: 443,
      extraConfig: { sni: "tokyo.example.com", "skip-cert-verify": true },
      egressIp: "1.2.3.4",
      egressTimezone: "Asia/Tokyo",
      status: "ENABLED",
      remark: "备注",
      secretConfigured: true,
      assignedUserName: "张三",
      groupId: null,
      groupName: null,
      sourceType: null,
      createdAt: "2026-08-18T10:00:00",
      updatedAt: "2026-08-18T10:00:00",
    };

    const form = nodeToForm(node);

    expect(form.secret).toEqual({ password: "" });
    expect(form.originalProtocol).toBe("TROJAN");
    expect(form.extraConfig).toEqual([
      { key: "sni", value: "tokyo.example.com" },
      { key: "skip-cert-verify", value: "true" },
    ]);
    expect(form.egressIp).toBe("1.2.3.4");
    expect(form.egressTimezone).toBe("Asia/Tokyo");
  });

  it("库里出口 IP 为 null 时回填成空串，表单输入框不显示「null」", () => {
    const node = {
      id: 4,
      name: "LAND-东京-04",
      role: "LAND",
      protocol: "TROJAN",
      serverAddr: "203.0.113.10",
      port: 443,
      extraConfig: {},
      egressIp: null,
      egressTimezone: null,
      status: "ENABLED",
      remark: null,
      secretConfigured: false,
      assignedUserName: null,
      groupId: null,
      groupName: null,
      sourceType: null,
      createdAt: "2026-08-18T10:00:00",
      updatedAt: "2026-08-18T10:00:00",
    } as AdminNodeResponse;

    expect(nodeToForm(node).egressIp).toBe("");
    expect(nodeToForm(node).egressTimezone).toBe("");
  });

  it('库里的 null 值不铺成表单行——String(null) 会变成字符串 "null" 再被当真值写回去', () => {
    const node = {
      id: 3,
      name: "LAND-东京-03",
      role: "LAND",
      protocol: "TROJAN",
      serverAddr: "tokyo.example.com",
      port: 443,
      extraConfig: { sni: "tokyo.example.com", "skip-cert-verify": null },
      egressIp: "1.2.3.4",
      egressTimezone: null,
      status: "ENABLED",
      remark: "备注",
      secretConfigured: true,
      assignedUserName: "张三",
      groupId: null,
      groupName: null,
      sourceType: null,
      createdAt: "2026-08-18T10:00:00",
      updatedAt: "2026-08-18T10:00:00",
    } as AdminNodeResponse;

    expect(nodeToForm(node).extraConfig).toEqual([{ key: "sni", value: "tokyo.example.com" }]);
  });
});

describe("nodeForm 对 MIHOMO 的处理", () => {
  const mihomoNode = {
    id: 1,
    name: "香港-01",
    role: "FRONT",
    protocol: "MIHOMO",
    serverAddr: "hk.example.com",
    port: 35355,
    extraConfig: {},
    egressIp: null,
    status: "ENABLED",
    remark: "",
    secretConfigured: true,
    assignedUserName: null,
    groupId: 3,
    groupName: "机场A",
    sourceType: "anytls",
    createdAt: "2026-08-21T00:00:00Z",
    updatedAt: "2026-08-21T00:00:00Z",
  } as AdminNodeResponse;

  it("MIHOMO 没有分键敏感配置，表单敏感键区为空", () => {
    expect(PROTOCOL_SECRET_KEYS.MIHOMO).toEqual([]);
    expect(nodeToForm(mihomoNode).secret).toEqual({});
  });

  it("MIHOMO 表单提交的 payload 敏感键为空对象（服务端语义：沿用原值）", () => {
    const form = nodeToForm(mihomoNode);
    form.name = "香港-01-改名";
    const payload = buildNodePayload(form);
    expect(payload.protocol).toBe("MIHOMO");
    expect(payload.secret).toEqual({});
    expect(payload.name).toBe("香港-01-改名");
  });
});
