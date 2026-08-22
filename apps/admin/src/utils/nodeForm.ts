import type { AdminNodeResponse, NodeProtocol, NodeRole, NodeSaveRequest, NodeStatus } from "../api/types";

/**
 * 各协议的敏感键，与服务端 NodeProtocol.secretKeys() 逐字镜像。
 * 这些键的值会被加密存储，绝不能出现在明文的 extraConfig 里——
 * 服务端对此有硬校验（回 110001），前端在提交前先拦一道，给的是能看懂的话。
 */
export const PROTOCOL_SECRET_KEYS: Record<NodeProtocol, string[]> = {
  TROJAN: ["password"],
  SOCKS5: ["username", "password"],
  VMESS: ["uuid"],
  // MIHOMO 整份参数都是敏感配置，不存在「按键区分」，表单也不允许选它
  MIHOMO: [],
};

/** 各协议常用的非敏感透传键，只作为新建表单的默认空行，用户可以随意增删 */
export const PROTOCOL_EXTRA_HINTS: Record<NodeProtocol, string[]> = {
  TROJAN: ["sni", "skip-cert-verify"],
  SOCKS5: ["tls", "udp"],
  VMESS: ["alterId", "cipher", "network"],
  MIHOMO: [],
};

export interface KeyValueRow {
  key: string;
  value: string;
}

export interface NodeFormModel {
  id: number | null;
  name: string;
  role: NodeRole;
  protocol: NodeProtocol;
  /** 这条记录在库里的协议；新建时为 null。用来判断敏感键是否必须重填，见 validateNodeForm */
  originalProtocol: NodeProtocol | null;
  serverAddr: string;
  port: number | null;
  /** 自由键值对，提交前转成对象 */
  extraConfig: KeyValueRow[];
  /** 键固定为该协议的敏感键；值留空表示沿用原值 */
  secret: Record<string, string>;
  /** 出口 IP，单条；留空提交 null 表示未填 */
  egressIp: string;
  /** 落地出口时区（IANA 时区名）；留空提交 null 表示未填 */
  egressTimezone: string;
  status: NodeStatus;
  remark: string;
}

/** 把协议的敏感键铺成一组空值，供表单渲染 */
function emptySecretKeys(protocol: NodeProtocol): Record<string, string> {
  return Object.fromEntries(PROTOCOL_SECRET_KEYS[protocol].map((key) => [key, ""]));
}

export function emptyNodeForm(role: NodeRole): NodeFormModel {
  const protocol: NodeProtocol = "TROJAN";
  return {
    id: null,
    name: "",
    role,
    protocol,
    originalProtocol: null,
    serverAddr: "",
    port: null,
    extraConfig: PROTOCOL_EXTRA_HINTS[protocol].map((key) => ({ key, value: "" })),
    secret: emptySecretKeys(protocol),
    egressIp: "",
    egressTimezone: "",
    status: "ENABLED",
    remark: "",
  };
}

/** 切换协议时重置敏感键与默认透传键提示，已填的自定义透传键保留 */
export function applyProtocol(form: NodeFormModel, protocol: NodeProtocol): NodeFormModel {
  const filledExtraRows = form.extraConfig.filter((row) => row.key && row.value);
  const suggestedKeys = PROTOCOL_EXTRA_HINTS[protocol]
    .filter((key) => !filledExtraRows.some((row) => row.key === key))
    .map((key) => ({ key, value: "" }));
  return { ...form, protocol, secret: emptySecretKeys(protocol), extraConfig: [...filledExtraRows, ...suggestedKeys] };
}

export function nodeToForm(node: AdminNodeResponse): NodeFormModel {
  return {
    id: node.id,
    name: node.name,
    role: node.role,
    protocol: node.protocol,
    originalProtocol: node.protocol,
    serverAddr: node.serverAddr,
    port: node.port,
    extraConfig: Object.entries(node.extraConfig ?? {})
      // null/undefined 不铺成行：String(null) 会变成字符串 "null"，
      // 保存时又被当作真值写回去，是个查不出来的静默腐化
      .filter(([, value]) => value !== null && value !== undefined)
      .map(([key, value]) => ({ key, value: String(value) })),
    // 服务端不回传密码，回填时一律是空的；留空提交即沿用原值
    secret: emptySecretKeys(node.protocol),
    egressIp: node.egressIp ?? "",
    egressTimezone: node.egressTimezone ?? "",
    status: node.status,
    remark: node.remark ?? "",
  };
}

/** 键值对编辑器里填的都是字符串，这里还原成 mihomo 需要的标量类型 */
export function parseScalar(raw: string): string | number | boolean {
  if (raw === "true") {
    return true;
  }
  if (raw === "false") {
    return false;
  }
  // 有前导零的值（如机房编号 007）保持字符串，避免被吃掉
  if (/^-?\d+(\.\d+)?$/.test(raw) && !/^0\d/.test(raw)) {
    return Number(raw);
  }
  return raw;
}

export function validateNodeForm(form: NodeFormModel): string[] {
  const errors: string[] = [];

  if (!form.name.trim()) {
    errors.push("节点名不能为空");
  }
  if (!form.serverAddr.trim()) {
    errors.push("节点地址不能为空");
  }
  if (form.port === null || form.port < 1 || form.port > 65535) {
    errors.push("端口必须在 1 到 65535 之间");
  }

  const secretKeys = PROTOCOL_SECRET_KEYS[form.protocol];
  const seenKeys = new Set<string>();
  for (const row of form.extraConfig) {
    const key = row.key.trim();
    if (!key) {
      continue;
    }
    if (secretKeys.includes(key)) {
      errors.push(`${key} 属于该协议的敏感键，必须填在「敏感配置」里，不能放进透传键`);
    }
    if (seenKeys.has(key)) {
      errors.push(`透传键 ${key} 重复`);
    }
    seenKeys.add(key);
  }

  // 编辑时切换了协议，敏感键就必须重填：留空在服务端是「沿用原值」，
  // 而原值是旧协议的键（如 password），会与新协议的 type 拼成一个取不到密钥的节点，
  // 且这种失效是静默的——保存成功、列表照常显示「已配置」，直到客户端连不上才发现
  if (form.originalProtocol !== null && form.originalProtocol !== form.protocol) {
    const missingKeys = Object.entries(form.secret)
      .filter(([, value]) => !value.trim())
      .map(([key]) => key);
    if (missingKeys.length > 0) {
      errors.push(`切换协议后必须重新填写敏感配置：${missingKeys.join("、")}`);
    }
  }

  // 出口 IP 会与客户端探测到的实际出口逐字比对，填个域名或坏值只会换来必然的校验失败
  const egressIp = form.egressIp.trim();
  if (egressIp && !isIpLiteral(egressIp)) {
    errors.push(`出口 IP「${egressIp}」不是合法的 IP 地址`);
  }

  // 时区存的是给后续业务直接消费的 IANA 名，坏值服务端也会 410015 挡回来，这里先给能看懂的话
  const egressTimezone = form.egressTimezone.trim();
  if (egressTimezone && !isIanaTimeZone(egressTimezone)) {
    errors.push(`出口时区「${egressTimezone}」不是合法的 IANA 时区名`);
  }

  return errors;
}

/** 是否为运行时认可的 IANA 时区名（含 UTC 等别名），与服务端 ZoneId.of 的校验同宽 */
export function isIanaTimeZone(value: string): boolean {
  try {
    new Intl.DateTimeFormat(undefined, { timeZone: value });
    return true;
  } catch {
    return false;
  }
}

/** 是否为 IPv4/IPv6 字面量。IPv6 只做形态校验，不含 zone id 与 v4 内嵌写法 */
export function isIpLiteral(value: string): boolean {
  const v4 = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(value);
  if (v4) {
    // 前导零一并拒绝：010 这类写法有八进制歧义，探测端也不会这么回显
    return v4.slice(1).every((octet) => Number(octet) <= 255 && !(octet.length > 1 && octet.startsWith("0")));
  }
  if (!value.includes(":") || value.includes(":::")) {
    return false;
  }
  if (value.split("::").length - 1 > 1) {
    return false;
  }
  const groups = value.split(":");
  if (groups.length > 8 || (!value.includes("::") && groups.length !== 8)) {
    return false;
  }
  return groups.every((group) => /^[0-9a-fA-F]{0,4}$/.test(group)) && groups.some((group) => group !== "");
}

/**
 * 地址改动后同步出口 IP 的预填：落地节点的地址是 IP 字面量时（单 IP VPS 的常态，
 * 出口就是它自己），把空的、或仍等于上一次地址值（说明此前也是预填的）的出口 IP
 * 跟随更新；管理员手工改过的值绝不覆盖。
 */
export function syncEgressIpFromServerAddr(form: NodeFormModel, previousServerAddr: string): NodeFormModel {
  const serverAddr = form.serverAddr.trim();
  if (form.role !== "LAND" || !isIpLiteral(serverAddr)) {
    return form;
  }
  const egressIp = form.egressIp.trim();
  if (egressIp !== "" && egressIp !== previousServerAddr.trim()) {
    return form;
  }
  return { ...form, egressIp: serverAddr };
}

/**
 * GeoIP 查询返回后同步出口时区的预填：只在时区为空、或仍等于上一次预填值
 * （说明此前也是预填的）时写入；管理员手工改过的值绝不覆盖。
 * 查询失败（fetchedTimezone 为 null）不动表单，降级为人工填写。
 */
export function syncEgressTimezoneFromLookup(
  form: NodeFormModel,
  fetchedTimezone: string | null,
  previousPrefill: string,
): NodeFormModel {
  if (form.role !== "LAND" || !fetchedTimezone) {
    return form;
  }
  const egressTimezone = form.egressTimezone.trim();
  if (egressTimezone !== "" && egressTimezone !== previousPrefill) {
    return form;
  }
  return { ...form, egressTimezone: fetchedTimezone };
}

export function buildNodePayload(form: NodeFormModel): NodeSaveRequest {
  const extraConfig: Record<string, unknown> = {};
  for (const row of form.extraConfig) {
    const key = row.key.trim();
    const value = row.value.trim();
    // 键或值任一为空都丢弃：新建表单默认铺了几行常用键的空行，
    // 原样提交会把 sni:"" 这样的空值下发给 mihomo
    if (key && value) {
      extraConfig[key] = parseScalar(value);
    }
  }

  // 只提交填了值的敏感键；一个都没填就提交空对象，服务端据此沿用原值
  const secret: Record<string, string> = {};
  for (const [key, value] of Object.entries(form.secret)) {
    if (value.trim()) {
      secret[key] = value.trim();
    }
  }

  return {
    name: form.name.trim(),
    role: form.role,
    protocol: form.protocol,
    serverAddr: form.serverAddr.trim(),
    port: form.port as number,
    extraConfig,
    secret,
    // 出口 IP 与出口时区都是落地节点的属性，第一跳节点一律不带；留空提交 null 表示未填
    egressIp: form.role === "LAND" ? form.egressIp.trim() || null : null,
    egressTimezone: form.role === "LAND" ? form.egressTimezone.trim() || null : null,
    status: form.status,
    remark: form.remark.trim(),
  };
}
