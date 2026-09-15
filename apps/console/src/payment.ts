/**
 * 支付方式拍平层：后端只有单一 stripe 通道，前端在纯展示层把它摊开成
 * 「微信支付 / 支付宝 / 银行卡」三个并列选项。子方式只决定渲染哪种确认 UI，不进任何接口参数。
 */

/** 展示顺序即数组顺序：微信 → 支付宝 → 银行卡 */
export const STRIPE_SUB_METHODS = ["wxpay", "alipay", "card"] as const;

export type StripeSubMethod = (typeof STRIPE_SUB_METHODS)[number];

/** 子方式 → Stripe payment_method_types（与服务端 PaymentProperties 的映射表逐字一致） */
export const STRIPE_PM_TYPE: Record<StripeSubMethod, string> = {
  wxpay: "wechat_pay",
  alipay: "alipay",
  card: "card",
};

export interface PayOption {
  key: string;
  subMethod: StripeSubMethod;
}

/** 从后端 methods 构建拍平列表；没有 stripe 通道就没有任何选项 */
export function buildPayOptions(methods: string[]): PayOption[] {
  if (!methods.includes("stripe")) {
    return [];
  }
  return STRIPE_SUB_METHODS.map((m) => ({ key: `stripe:${m}`, subMethod: m }));
}

/** 已支付 / 轮询可停口径：控制台的订单 PAID 即终态（履约在同事务），没有 COMPLETED */
export function isPaidStatus(status: string): boolean {
  return status === "PAID";
}
