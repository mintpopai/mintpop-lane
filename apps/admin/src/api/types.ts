/**
 * 与服务端 ai.mintpop.pier.enumeration 逐字镜像的枚举。
 * 成员名与字符串取值一致、全大写下划线；改任一端两端同步。
 */
export const USER_STATUS = {
  ACTIVE: "ACTIVE",
  SUSPENDED: "SUSPENDED",
  REVOKED: "REVOKED",
} as const;
export type UserStatus = (typeof USER_STATUS)[keyof typeof USER_STATUS];

export const USER_ROLE = {
  ADMIN: "ADMIN",
  MEMBER: "MEMBER",
} as const;
export type UserRole = (typeof USER_ROLE)[keyof typeof USER_ROLE];

export const NODE_ROLE = {
  FRONT: "FRONT",
  LAND: "LAND",
} as const;
export type NodeRole = (typeof NODE_ROLE)[keyof typeof NODE_ROLE];

export const NODE_PROTOCOL = {
  TROJAN: "TROJAN",
  SOCKS5: "SOCKS5",
  VMESS: "VMESS",
} as const;
export type NodeProtocol = (typeof NODE_PROTOCOL)[keyof typeof NODE_PROTOCOL];

export const NODE_STATUS = {
  ENABLED: "ENABLED",
  DISABLED: "DISABLED",
} as const;
export type NodeStatus = (typeof NODE_STATUS)[keyof typeof NODE_STATUS];

/** 界面上的中文标签。取值是枚举，展示是中文，两者不混用 */
export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: "正常",
  SUSPENDED: "已停用",
  REVOKED: "已吊销",
};

export const USER_ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: "管理员",
  MEMBER: "普通成员",
};

export const NODE_ROLE_LABELS: Record<NodeRole, string> = {
  FRONT: "第一跳（出国）",
  LAND: "第二跳（落地）",
};

export const NODE_STATUS_LABELS: Record<NodeStatus, string> = {
  ENABLED: "启用",
  DISABLED: "禁用",
};

/** 统一返回体。HTTP 一律 200，成败只看 code */
export interface ApiResponse<T> {
  code: number;
  data: T | null;
  msg: string | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface AdminUserResponse {
  id: number;
  subject: string;
  name: string;
  role: UserRole;
  status: UserStatus;
  frontNodeId: number;
  frontNodeName: string | null;
  landNodeId: number | null;
  landNodeName: string | null;
  /** 取自其落地节点，未分配时是空数组 */
  egressIps: string[];
  /** 服务端不回传凭据本身，只告诉你配没配 */
  credentialConfigured: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminNodeResponse {
  id: number;
  name: string;
  role: NodeRole;
  protocol: NodeProtocol;
  serverAddr: string;
  port: number;
  extraConfig: Record<string, unknown>;
  egressIps: string[];
  status: NodeStatus;
  remark: string | null;
  /** 同上，密码不回传，只告诉你配没配 */
  secretConfigured: boolean;
  /** 该落地节点当前的占用者姓名；未分配或非 LAND 时为 null */
  assignedUserName: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 新建 / 更新用户的入参。刻意没有 role：提权只能改库 */
export interface UserSaveRequest {
  subject: string;
  name: string;
  status: UserStatus;
  frontNodeId: number;
  landNodeId: number | null;
  /** 空串表示沿用原值，不会把已有凭据清掉 */
  claudeCredential: string;
}

export interface NodeSaveRequest {
  name: string;
  role: NodeRole;
  protocol: NodeProtocol;
  serverAddr: string;
  port: number;
  extraConfig: Record<string, unknown>;
  /** 空对象表示沿用原值，不会把已有密码清掉 */
  secret: Record<string, string>;
  egressIps: string[];
  status: NodeStatus;
  remark: string;
}

export interface UserPageQuery {
  keyword: string;
  pageNo: number;
  pageSize: number;
}
