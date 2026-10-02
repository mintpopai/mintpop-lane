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
  FRONT: "机场订阅",
  LAND: "落地节点",
};

export const NODE_STATUS_LABELS: Record<NodeStatus, string> = {
  ENABLED: "启用",
  DISABLED: "禁用",
};

export const CURRENCY = {
  USD: "USD",
  CNY: "CNY",
} as const;
export type Currency = (typeof CURRENCY)[keyof typeof CURRENCY];

export const CURRENCY_LABELS: Record<Currency, string> = {
  USD: "美元",
  CNY: "人民币",
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

/** 换机申请状态。取值与服务端 RebindRequestStatus 逐字一致 */
export const REBIND_REQUEST_STATUS = {
  PENDING: "PENDING",
  APPROVED: "APPROVED",
  REJECTED: "REJECTED",
  SUPERSEDED: "SUPERSEDED",
} as const;
export type RebindRequestStatus =
  (typeof REBIND_REQUEST_STATUS)[keyof typeof REBIND_REQUEST_STATUS];

/** 状态 → 中文标签。SUPERSEDED 是「用户又提了新申请，这条自动作废」，不是管理员拒的 */
export const REBIND_REQUEST_STATUS_LABELS: Record<RebindRequestStatus, string> = {
  PENDING: "待处理",
  APPROVED: "已同意",
  REJECTED: "已拒绝",
  SUPERSEDED: "已作废",
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
  role: UserRole;
  subscriptions: MeSubscription[];
}

export interface MeSubscription {
  id: number;
  /** 分配号：给用户看的分配标识 */
  assignmentNo: string;
  name: string;
  /** 服务端可能新增本前端不认识的类型，故用 string 承载 */
  agentType: string;
  /** 起止为 null 即「待开通」：自助购买建出、管理员尚未填起期 */
  startsAt: string | null;
  endsAt: string | null;
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
  role: UserRole;
  status: UserStatus;
  /** 该用户当前分配到的第一跳，按机场订阅顺位排列；未分配时为空数组 */
  frontSubscriptions: FrontSubscriptionBrief[];
  landNodeId: number | null;
  landNodeName: string | null;
  /** 取自其落地节点，未分配或落地未填出口时为 null */
  egressIp: string | null;
  /** 在期订阅摘要，一眼看出这个人开了什么、到什么时候 */
  activeSubscriptions: ActiveSubscriptionBrief[];
  /** 管理员自用说明，没写时为 null */
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 用户第一跳列表的一项 */
export interface FrontSubscriptionBrief {
  /** 顺位：0 主用，1、2 备用 */
  position: number;
  airportSubscriptionId: number;
  airportName: string;
  subscriptionName: string;
  account: string;
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
  /** 出口 IP，仅 LAND 节点有值；未填为 null */
  egressIp: string | null;
  /** 落地出口 IP 对应的 IANA 时区名，仅 LAND 节点有值；未填为 null */
  egressTimezone: string | null;
  status: NodeStatus;
  remark: string | null;
  /** 同上，密码不回传，只告诉你配没配 */
  secretConfigured: boolean;
  /** 落地节点容量（最多可绑定的用户数）；非 LAND 为 null */
  capacity: number | null;
  /** 该落地节点当前绑定的用户数；非 LAND 为 null */
  assignedUserCount: number | null;
  /** 所属机场订阅；手工节点为 null */
  airportSubscriptionId: number | null;
  airportSubscriptionName: string | null;
  /** 订阅节点的真实 mihomo type（如 anytls）；手工节点为 null */
  sourceType: string | null;
  /** 故障域：节点域名 CNAME 链的终点，仅对 FRONT 节点有意义；null 表示尚未解析或解析失败 */
  failureDomain: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * 更新用户的入参。用户由登录自动建档，这里只管管理员能动的部分：
 * 处置态与链路资源分配。subject/email 随身份走、role 提权只能改库。
 */
export interface UserSaveRequest {
  status: UserStatus;
  landNodeId: number | null;
  /** 管理员自用说明，空串表示没写。整体保存接口，不带就等于清空 */
  remark: string;
}

/** 落地节点连通性检测结果；「不通」也是正常返回（reachable=false + error），不是请求错误 */
export interface NodeProbeResponse {
  /** 经该节点能否访问公网 */
  reachable: boolean;
  /** 探测耗时（毫秒）；不通时是失败前耗掉的时间 */
  latencyMs: number;
  /** 探测到的实际出口 IP；不通时为 null */
  actualEgressIp: string | null;
  /** 节点上登记的出口 IP；未填为 null */
  registeredEgressIp: string | null;
  /** 实际与登记是否一致；任一侧缺失（未登记 / 不通）时为 null */
  matched: boolean | null;
  /** 不通的原因摘要；连通时为 null */
  error: string | null;
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
  /** 出口 IP，仅 LAND 需要；null 表示未填 */
  egressIp: string | null;
  /** 落地出口时区（IANA 时区名），仅 LAND 需要；null 表示未填 */
  egressTimezone: string | null;
  /** 落地节点容量（最多可绑定的用户数），仅 LAND 需要；非 LAND 提交 null */
  capacity: number | null;
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

/** 一台已知设备的展示信息。机器码本身不下发到管理端——管理员靠这三样辨认是哪台机器 */
export interface DeviceBrief {
  name: string;
  os: string;
  model: string;
  /** 最近一次上报时刻：这台机器最后一次还在用是什么时候。服务端按 5 分钟窗口节流刷新，可能滞后数分钟 */
  lastSeenAt: string;
}

/** 订阅当前绑定的设备。未绑定时整个对象为 null，不是字段为空 */
export interface BoundDevice extends DeviceBrief {
  boundAt: string;
}

/**
 * 管理端的订阅视图。凭据只回传有没有录，本体一个字符不出现。
 * 套餐信息（名称/时长/价格/币种）是分配时的快照，套餐后续改动不影响这里。
 */
export interface AdminSubscriptionResponse {
  id: number;
  /** 分配号：给用户看的分配标识，10 位 Crockford Base32 短码；程序内部引用走自增 id */
  assignmentNo: string;
  userId: number;
  /** 归属企业 id；null 表示个人订阅 */
  enterpriseId: number | null;
  agentType: string;
  /** 所选套餐 id。弱引用，套餐硬删后允许悬空 */
  planId: number;
  name: string;
  /** 套餐时长快照（天）：止期 = 起期 + 本值 */
  planDurationDays: number;
  planPrice: number;
  /** 服务端可能新增币种，故用 string 承载 */
  planCurrency: string;
  /** 起止为 null 即「待开通」：自助购买建出、管理员尚未填起期，填了即开通 */
  startsAt: string | null;
  endsAt: string | null;
  /** 本次分配给用户的账号邮箱，小写；null 表示未录 */
  accountEmail: string | null;
  hasCredential: boolean;
  /** 凭证到期时刻；未签发过时为 null */
  credentialExpiresAt: string | null;
  /** 凭证到期日与订阅止期已脱节（订阅止期改过但凭证没重签），需要重新签发 */
  credentialStale: boolean;
  /**
   * 席位所属组织未开启 usage credits，该席位的 Fable 5 不可用（凭证本身仍有效）。
   * 仅在签发时明确探测到「未开启」才为 true；旧式/手工凭证无从得知，一律 false。
   */
  extraUsageDisabled: boolean;
  /** 当前绑定的设备；null 表示这份订阅还没在任何设备上用过 */
  boundDevice: BoundDevice | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 发起凭证签发（POST /admin/subscriptions/{id}/credential/authorize-url）的返回 */
export interface CredentialAuthorizationStart {
  authUrl: string;
  sessionId: string;
  /** 该订阅当前录入的账号邮箱；未录入为 null，此时需要管理员自行确认要登录哪个账号 */
  accountEmail: string | null;
  egressIp: string;
}

/** 兑换凭证（POST /admin/subscriptions/{id}/credential/exchange）的入参 */
export interface CredentialExchangeRequest {
  sessionId: string;
  code: string;
}

/** 兑换凭证成功后的结果 */
export interface CredentialIssueResult {
  accountEmail: string | null;
  grantedScope: string;
  expiresAt: string;
}

/**
 * 吊销凭证（POST /admin/subscriptions/{id}/credential/revoke）的返回。
 * 无论上游是否吊销成功，本地凭证与全部签发元数据都会被清空；
 * upstreamRevoked 只表示上游 Anthropic 是否确认吊销成功——为 false 时
 * 该凭证在上游侧可能仍然有效，界面必须如实区分，不能都说成「已吊销」。
 */
export interface CredentialRevokeResult {
  upstreamRevoked: boolean;
}

/** 一条换机申请。用户邮箱、套餐名、分配号由服务端联查后一并下发，管理端不再二次取数 */
export interface AdminDeviceRebindRequestResponse {
  id: number;
  /** 申请号，飞书卡片里引用的就是它 */
  requestNo: string;
  subscriptionId: number;
  /** 订阅已被删除时为空串，不是占位文案 */
  subscriptionName: string;
  /** 订阅已被删除时为空串，不是占位文案 */
  assignmentNo: string;
  userId: number;
  userEmail: string;
  /** 申请时绑定的设备；此前从未绑定过则为 null */
  fromDevice: DeviceBrief | null;
  /** 申请要换到的设备；对应设备行事后被删除时为 null（服务端 doc：唯一成因是设备行已删，不是“从未绑定”） */
  toDevice: DeviceBrief | null;
  reason: string | null;
  status: RebindRequestStatus;
  createdAt: string;
  /** 处理时刻；PENDING 时为 null */
  decidedAt: string | null;
}

/** 分配订阅的入参。只能选现有套餐，agent 类型与止期都由所选套餐决定 */
export interface SubscriptionCreateRequest {
  planId: number;
  /** 归属企业 id；null 表示个人订阅 */
  enterpriseId: number | null;
  startsAt: string;
  /** 分配出去的账号邮箱，小写；空串表示未录 */
  accountEmail: string;
  credential: string;
  remark: string;
}

/** 更新订阅的入参。套餐不可换；credential 留空表示沿用原值 */
export interface SubscriptionUpdateRequest {
  /** 归属企业 id；null 表示个人订阅。与凭据不同，这里留空就是清除归属 */
  enterpriseId: number | null;
  startsAt: string;
  /** 分配出去的账号邮箱，小写。与凭据不同，这里留空就是清除 */
  accountEmail: string;
  credential: string;
  remark: string;
}

/** 管理端的机场视图 */
export interface AirportResponse {
  id: number;
  name: string;
  websiteUrl: string | null;
  remark: string | null;
  /** 是否主用机场：false 时其订阅只当备用，不会被分配为主用 */
  primaryEnabled: boolean;
  subscriptionCount: number;
  /** 各订阅主用人数之和 */
  primaryUsed: number;
  /** 各订阅主用名额之和：带宽 ÷ 每人带宽（全局配置）向下取整；非主用机场恒为 0 */
  primaryCapacity: number;
  createdAt: string;
  updatedAt: string;
}

export interface AirportSaveRequest {
  name: string;
  websiteUrl: string;
  remark: string;
  primaryEnabled: boolean;
}

/** 管理端的机场订阅视图。订阅链接只回显打码形态，token 不出现 */
export interface AirportSubscriptionResponse {
  id: number;
  name: string;
  subUrlMasked: string;
  nodeCount: number;
  remark: string | null;
  /** 所属机场 id */
  airportId: number;
  airportName: string;
  /** 机场账号（邮箱等），用于登录机场官网续费/查流量 */
  account: string;
  /** 带宽（Mbps），创建后不可改；主用容量 = 本值 ÷ 每人带宽（全局配置）向下取整 */
  bandwidthMbps: number;
  /** 已用流量字节数；机场未返回额度头则为 null */
  usedBytes: number | null;
  /** 总流量额度字节数；null 同上 */
  totalBytes: number | null;
  /** 订阅到期时间；null 同上 */
  expiresAt: string | null;
  /** 最近一次成功拉取订阅的时间；从未拉取成功过则为 null */
  fetchedAt: string | null;
  /** 订阅拉取连续失败的起始时间；最近一次拉取成功则为 null */
  fetchFailedSince: string | null;
  /** 最近一次拉取失败的错误说明；拉取成功后为 null */
  lastFetchError: string | null;
  /** 本订阅当前占用的主用名额数（第一跳顺位 0 引用本订阅的用户数） */
  primaryUsed: number;
  /** 本订阅的主用名额总容量：bandwidthMbps ÷ 每人带宽（全局配置）向下取整 */
  primaryCapacity: number;
  createdAt: string;
  updatedAt: string;
}

/** 订阅尽调入参：候选机场的试用订阅链接 */
export interface SubAuditRequest {
  subUrl: string;
}

/** 尽调报告里的一个故障域条目；镜像服务端 SubAuditResponse.FailureDomainReport */
export interface SubAuditFailureDomainReport {
  domain: string;
  nodeCount: number;
  /** 按名称启发式判定为美国落地的节点数 */
  usNodeCount: number;
}

/**
 * 订阅尽调报告：给一个候选机场的试用订阅链接，判断它是否与库里已有节点撞故障域。
 * 全程只读，不写库；conflictsWith 非空即应否决这次采购。
 */
export interface SubAuditResponse {
  /** 机场名，取自订阅响应头；取不到为 null */
  airportName: string | null;
  totalNodes: number;
  /** 按名称启发式判定为美国落地的节点数 */
  usNodeCount: number;
  /** 判定出的美国节点名，原样列出供人核对——判定是启发式的，不做纯自动决策 */
  usNodeNames: string[];
  failureDomains: SubAuditFailureDomainReport[];
  /** 与库中已有节点撞故障域的分组名；非空即应否决这次采购 */
  conflictsWith: string[];
  /** 订阅里出现过的 mihomo type 集合，用于确认 front-tuning 覆盖表是否已支持 */
  protocols: string[];
  usedBytes: number | null;
  totalBytes: number | null;
  expiresAt: string | null;
}

/** 建机场订阅入参：订阅里的美国节点由服务端自动导入 */
export interface AirportSubscriptionCreateRequest {
  name: string;
  subUrl: string;
  /** 所属机场 id，创建后不可改 */
  airportId: number;
  /** 机场账号 */
  account: string;
  /** 带宽（Mbps），创建后不可改 */
  bandwidthMbps: number;
  remark: string;
}

/** 更新入参：只能改名称、账号、备注；所属机场与带宽创建后不可改，换机场/带宽等于建新订阅 */
export interface AirportSubscriptionUpdateRequest {
  name: string;
  account: string;
  remark: string;
}

/** 管理端的套餐视图 */
export interface PlanResponse {
  id: number;
  name: string;
  /** 本套餐面向的 agent 类型。服务端可能新增本前端不认识的类型，故用 string 承载 */
  agentType: string;
  /** 套餐时长（天） */
  durationDays: number;
  price: number;
  currency: Currency;
  /** 面向用户的短描述，控制台购买卡片的副标题 */
  description: string | null;
  /** 套餐图公开 URL */
  imageUrl: string | null;
  /** 详情富文本（已经服务端 jsoup 白名单净化），留空表示不展示「详情」入口 */
  detail: string | null;
  /** 上架状态：false 表示停用但保留 */
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 管理端的企业视图 */
export interface EnterpriseResponse {
  id: number;
  name: string;
  /** 企业域名，小写 */
  domain: string;
  /** 本企业支持的 agent 类型。服务端可能新增本前端不认识的类型，故用 string 承载 */
  agentTypes: string[];
  /** 启用状态：false 表示停用但保留 */
  enabled: boolean;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 新建/更新企业的入参，更新时全量覆盖 */
export interface EnterpriseSaveRequest {
  name: string;
  domain: string;
  agentTypes: AgentType[];
  enabled: boolean;
  remark: string;
}

/** 新建/更新套餐的入参，更新时全量覆盖 */
export interface PlanSaveRequest {
  name: string;
  agentType: AgentType;
  durationDays: number;
  price: number;
  currency: Currency;
  description: string;
  imageUrl: string;
  /** 详情富文本，入库前经服务端 jsoup 白名单净化 */
  detail: string;
  enabled: boolean;
  remark: string;
}

/** 图片上传结果（POST /api/admin/uploads/images） */
export interface ImageUploadResponse {
  url: string;
}

/**
 * 管理端「链路健康」查询结果（GET /admin/link-health）：故障域 × 运营商成功率矩阵
 * （spec §8.3）。矩阵全库跨全部用户求和——分析维度只到故障域与运营商，
 * 不按节点也不按用户，页面渲染时同样只能按这两个维度展开，不能顺手加节点明细。
 */
export interface LinkHealthResponse {
  /** 故障域 × 运营商的成功率矩阵，按故障域分组 */
  domains: LinkHealthDomainRow[];
}

/**
 * 一个故障域下按运营商展开的一行。samples/aliveCount/failovers 是该故障域下全部运营商
 * （含未知运营商）之和。
 *
 * failureDomain 为空串表示"尚未解析出故障域"（与 link_report 的存储编码一致），前端要显式
 * 显示成「未解析」，不能显示成空白——故障域未解析意味着这组节点的冗余情况是未知的，
 * 比"只有一个故障域"更糟（与用户详情页「入口无冗余」同一口径）。
 */
export interface LinkHealthDomainRow {
  failureDomain: string;
  samples: number;
  aliveCount: number;
  failovers: number;
  asns: LinkHealthAsnCell[];
}

/**
 * 一个"故障域 × 运营商"格子。运营商维度的**键是 ASN**（形如 AS4134），orgName 只是标签——
 * 上游对同一个 ASN 的文案会漂（今天 China Telecom、明天 CHINANET-BACKBONE），服务端因此只按
 * ASN 分列，名字另从 asn_org 取。
 *
 * 这里有**三种含义完全不同的"空"**，页面上必须分得开，不能笼统当成"没值"：
 * - asn 为空串：这一组样本的 ASN 反查全部失败，连是谁都不知道 → 显示「未知运营商」；
 * - orgName 为 null：ASN 知道，但 asn_org 里还没记过展示名（旁路写入允许缺）→ 退回显示 ASN 串。
 *   **绝不能因为没名字就把整列藏掉**——少一列等于凭空丢掉一批真实流量，比显示一串 AS 号糟得多；
 * - successRate 为 null：samples 为 0，不是 0——"没有数据"与"全挂"是两回事：把没有样本的
 *   格子画成 0% 会让人以为某个运营商彻底不通，实际只是这段时间没人从那个运营商上来。
 */
export interface LinkHealthAsnCell {
  asn: string;
  orgName: string | null;
  samples: number;
  aliveCount: number;
  successRate: number | null;
}

/** 第一跳节点筛选地区，与服务端 NodeRegion 逐字对应 */
export const NODE_REGION = {
  US: "US",
} as const;
export type NodeRegion = (typeof NODE_REGION)[keyof typeof NODE_REGION];

export interface RegionOption {
  value: NodeRegion;
  label: string;
}

/** 全局配置页读视图 */
export interface FrontSettingsResponse {
  region: NodeRegion;
  regionOptions: RegionOption[];
  /** 每个用户分配几家机场的订阅：1 主用 + n-1 备用 */
  airportsPerUser: number;
  /** 每个用户按多少带宽（Mbps）计主用名额 */
  bandwidthPerUserMbps: number;
}

export interface FrontSettingsUpdateRequest {
  region: NodeRegion;
  airportsPerUser: number;
  bandwidthPerUserMbps: number;
}

export const FRONT_REBUILD_PHASE = {
  IDLE: "IDLE",
  RUNNING: "RUNNING",
  SUCCEEDED: "SUCCEEDED",
  FAILED: "FAILED",
} as const;
export type FrontRebuildPhase = (typeof FRONT_REBUILD_PHASE)[keyof typeof FRONT_REBUILD_PHASE];

/** 最近一次全体重算的状态；服务端进程内保存，重启后回到 IDLE */
export interface FrontRebuildStatus {
  phase: FrontRebuildPhase;
  startedAt: string | null;
  finishedAt: string | null;
  userCount: number | null;
  subscriptionCount: number | null;
  error: string | null;
}

/** 容量预检：需要 = 有激活席位的用户数，现有 = 有节点的订阅主用名额之和 */
export interface FrontRebuildPreview {
  requiredPrimary: number;
  availablePrimary: number;
  sufficient: boolean;
}

/** 桌面端最新版本的拉取状态：服务端据此强制落后的桌面端更新；进程内保存，重启后重新拉取 */
export interface ClientVersionStatus {
  /** 当前认定的最新版本，如 1.2.0；从未拉到过为 null（此时只拦不带版本号的旧客户端） */
  latest: string | null;
  /** 最近一次拉取成功的时刻 */
  fetchedAt: string | null;
  /** 最近一次尝试拉取的时刻 */
  lastAttemptAt: string | null;
  /** 最近一次尝试是否失败；失败时 latest 沿用上一次拉到的值 */
  lastAttemptFailed: boolean;
  /** 读取的更新清单地址 */
  manifestUrl: string;
}
