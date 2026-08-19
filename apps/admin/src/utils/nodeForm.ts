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
};

/** 各协议常用的非敏感透传键，只作为新建表单的默认空行，用户可以随意增删 */
export const PROTOCOL_EXTRA_HINTS: Record<NodeProtocol, string[]> = {
  TROJAN: ["sni", "skip-cert-verify"],
  SOCKS5: ["tls", "udp"],
  VMESS: ["alterId", "cipher", "network"],
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
  /** 一行一个出口 IP，提交前拆分 */
  egressIpsText: string;
  status: NodeStatus;
  remark: string;
}

/** 把协议的敏感键铺成一组空值，供表单渲染 */
function 空敏感键(protocol: NodeProtocol): Record<string, string> {
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
    secret: 空敏感键(protocol),
    egressIpsText: "",
    status: "ENABLED",
    remark: "",
  };
}

/** 切换协议时重置敏感键与默认透传键提示，已填的自定义透传键保留 */
export function applyProtocol(form: NodeFormModel, protocol: NodeProtocol): NodeFormModel {
  const 已填的透传键 = form.extraConfig.filter((row) => row.key && row.value);
  const 建议键 = PROTOCOL_EXTRA_HINTS[protocol]
    .filter((key) => !已填的透传键.some((row) => row.key === key))
    .map((key) => ({ key, value: "" }));
  return { ...form, protocol, secret: 空敏感键(protocol), extraConfig: [...已填的透传键, ...建议键] };
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
    extraConfig: Object.entries(node.extraConfig ?? {}).map(([key, value]) => ({
      key,
      value: String(value),
    })),
    // 服务端不回传密码，回填时一律是空的；留空提交即沿用原值
    secret: 空敏感键(node.protocol),
    egressIpsText: (node.egressIps ?? []).join("\n"),
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

  const 敏感键 = PROTOCOL_SECRET_KEYS[form.protocol];
  const 见过的键 = new Set<string>();
  for (const row of form.extraConfig) {
    const key = row.key.trim();
    if (!key) {
      continue;
    }
    if (敏感键.includes(key)) {
      errors.push(`${key} 属于该协议的敏感键，必须填在「敏感配置」里，不能放进透传键`);
    }
    if (见过的键.has(key)) {
      errors.push(`透传键 ${key} 重复`);
    }
    见过的键.add(key);
  }

  // 编辑时切换了协议，敏感键就必须重填：留空在服务端是「沿用原值」，
  // 而原值是旧协议的键（如 password），会与新协议的 type 拼成一个取不到密钥的节点，
  // 且这种失效是静默的——保存成功、列表照常显示「已配置」，直到客户端连不上才发现
  if (form.originalProtocol !== null && form.originalProtocol !== form.protocol) {
    const 未填 = Object.entries(form.secret)
      .filter(([, value]) => !value.trim())
      .map(([key]) => key);
    if (未填.length > 0) {
      errors.push(`切换协议后必须重新填写敏感配置：${未填.join("、")}`);
    }
  }

  for (const ip of 拆出口IP(form.egressIpsText)) {
    if (/\s/.test(ip)) {
      errors.push(`出口 IP「${ip}」格式不对，一行填一个`);
    }
  }

  return errors;
}

/** 按行拆，去空白与空行；一行里若混了空格也原样留着，交给校验去报错 */
function 拆出口IP(text: string): string[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
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
    // 出口 IP 是落地节点的属性，第一跳节点一律不带
    egressIps: form.role === "LAND" ? 拆出口IP(form.egressIpsText) : [],
    status: form.status,
    remark: form.remark.trim(),
  };
}
