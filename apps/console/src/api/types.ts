/**
 * 与服务端 ai.mintpop.lane.enumeration 逐字镜像的枚举。
 * 成员名与字符串取值一致、全大写下划线；改任一端两端同步。
 */
export const AGENT_TYPE = {
  CLAUDE: "CLAUDE",
  CODEX: "CODEX",
} as const;
export type AgentType = (typeof AGENT_TYPE)[keyof typeof AGENT_TYPE];

export const AGENT_TYPE_LABELS: Record<AgentType, string> = {
  CLAUDE: "Claude Code",
  CODEX: "Codex",
};

export const CURRENCY = {
  USD: "USD",
  CNY: "CNY",
} as const;
export type Currency = (typeof CURRENCY)[keyof typeof CURRENCY];

export const ORDER_STATUS = {
  PENDING: "PENDING",
  PAID: "PAID",
  CANCELLED: "CANCELLED",
  EXPIRED: "EXPIRED",
  FAILED: "FAILED",
} as const;
export type OrderStatus = (typeof ORDER_STATUS)[keyof typeof ORDER_STATUS];

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: "待支付",
  PAID: "已支付",
  CANCELLED: "已取消",
  EXPIRED: "已过期",
  FAILED: "支付失败",
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
  /** 服务端有 ADMIN / MEMBER 之分，控制台不据此分支，只原样承载 */
  role: string;
  subscriptions: MeSubscription[];
}

export interface MeSubscription {
  id: number;
  /** 分配号：给用户看的分配标识，10 位短码 */
  assignmentNo: string;
  name: string;
  /** 服务端可能新增本前端不认识的类型，故用 string 承载 */
  agentType: string;
  /** 起止为 null 即「待开通」：付款后订阅已建出，管理员尚未填起期 */
  startsAt: string | null;
  endsAt: string | null;
  /** 服务端按当前时间算好的在期标记 */
  active: boolean;
}

/** 上架套餐（GET /api/plans） */
export interface PlanResponse {
  id: number;
  name: string;
  agentType: string;
  durationDays: number;
  price: number;
  /** 服务端可能新增币种，故用 string 承载 */
  currency: string;
  /** 面向用户的短描述，可空 */
  description: string | null;
  /** 套餐图公开 URL，可空 */
  imageUrl: string | null;
  /** 套餐详情富文本，已由服务端净化；可空，为空则不显示「详情」入口 */
  detail: string | null;
}

export interface OrderCreateRequest {
  planId: number;
}

export interface OrderCreateResponse {
  orderNo: string;
  amountMinor: number;
  currency: string;
}

/** 我的订单（GET /api/orders） */
export interface OrderResponse {
  orderNo: string;
  name: string;
  agentType: string;
  planDurationDays: number;
  planPrice: number;
  planCurrency: string;
  /** 最小货币单位整数 */
  amountMinor: number;
  status: OrderStatus;
  paidAt: string | null;
  /** 履约建出的订阅 id；未支付为 null */
  subscriptionId: number | null;
  createdAt: string;
}

/** 收银台信息：methods 为空即支付未开放 */
export interface CheckoutInfo {
  methods: string[];
  stripePublishableKey: string | null;
}

export interface PaymentIntentInfo {
  orderNo: string;
  clientSecret: string;
  amountMinor: number;
  currency: string;
  productName: string;
  expireRemainingSeconds: number;
}

export interface VerifyOrderRequest {
  orderNo: string;
}

export interface VerifyOrderResponse {
  orderNo: string;
  status: OrderStatus;
}
