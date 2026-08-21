/**
 * 与服务端 ai.mintpop.lane.enumeration 逐字镜像的枚举。
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
  /** 订阅导入的通用透传协议：整份 mihomo 参数加密存储，不能手工新建 */
  MIHOMO: "MIHOMO",
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

export const AGENT_TYPE = {
  CLAUDE: "CLAUDE",
  CODEX: "CODEX",
} as const;
export type AgentType = (typeof AGENT_TYPE)[keyof typeof AGENT_TYPE];

export const AGENT_TYPE_LABELS: Record<AgentType, string> = {
  CLAUDE: "Claude Code",
  CODEX: "Codex",
};

/** 统一返回体。HTTP 一律 200，成败只看 code */
export interface ApiResponse<T> {
  code: number;
  data: T | null;
  msg: string | null;
}

/** 当前登录者视图（/api/me）。无任何凭据字段 */
export interface MeResponse {
  id: number;
  email: string;
  name: string;
  role: UserRole;
  subscriptions: MeSubscription[];
}

export interface MeSubscription {
  id: number;
  name: string;
  /** 服务端可能新增本前端不认识的类型，故用 string 承载 */
  agentType: string;
  startsAt: string;
  endsAt: string;
  active: boolean;
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
  email: string;
  name: string;
  role: UserRole;
  status: UserStatus;
  /** 注册即无资源，未分配时为 null */
  frontNodeId: number | null;
  frontNodeName: string | null;
  landNodeId: number | null;
  landNodeName: string | null;
  /** 取自其落地节点，未分配时是空数组 */
  egressIps: string[];
  /** 在期订阅摘要，一眼看出这个人开了什么、到什么时候 */
  activeSubscriptions: ActiveSubscriptionBrief[];
  createdAt: string;
  updatedAt: string;
}

export interface ActiveSubscriptionBrief {
  id: number;
  name: string;
  /** 服务端可能新增本前端不认识的类型，故用 string 承载 */
  agentType: string;
  endsAt: string;
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
  /** 所属分组；手工节点为 null */
  groupId: number | null;
  groupName: string | null;
  /** 订阅节点的真实 mihomo type（如 anytls）；手工节点为 null */
  sourceType: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * 更新用户的入参。用户由登录自动建档，这里只管管理员能动的部分：
 * 处置态与链路资源分配。subject/email/name 随身份走、role 提权只能改库。
 */
export interface UserSaveRequest {
  status: UserStatus;
  frontNodeId: number | null;
  landNodeId: number | null;
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
  /** null = 不按有无在期订阅筛选 */
  hasActiveSubscription: boolean | null;
  pageNo: number;
  pageSize: number;
}

/** 管理端的订阅视图。凭据只回传有没有录，本体一个字符不出现 */
export interface AdminSubscriptionResponse {
  id: number;
  userId: number;
  agentType: string;
  name: string;
  startsAt: string;
  endsAt: string;
  hasCredential: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 新建/更新订阅的入参。更新时 credential 留空表示沿用原值 */
export interface SubscriptionSaveRequest {
  agentType: AgentType;
  name: string;
  startsAt: string;
  endsAt: string;
  credential: string;
  remark: string;
}

/** 管理端的分组视图。订阅链接只回显打码形态，token 不出现 */
export interface NodeGroupResponse {
  id: number;
  name: string;
  subUrlMasked: string;
  nodeCount: number;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 订阅预览里的一个条目，只有展示字段 */
export interface SubPreviewNode {
  sourceName: string;
  sourceType: string;
  serverAddr: string;
  port: number;
  /** 名称像「剩余流量/到期时间」的信息假条目，默认不勾选 */
  suspectedInfo: boolean;
  /** 该分组内是否已入池；新链接预览恒为 false */
  existed: boolean;
}

export interface SubPreviewRequest {
  subUrl: string;
}

export interface NodeGroupCreateRequest {
  name: string;
  subUrl: string;
  selectedNames: string[];
  remark: string;
}

/** 改名入参。不支持改订阅链接——换链接等于建新分组 */
export interface NodeGroupRenameRequest {
  name: string;
  remark: string;
}

export interface NodeGroupImportRequest {
  selectedNames: string[];
}
