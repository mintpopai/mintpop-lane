/**
 * Stripe.js 确认层：懒加载 SDK，并按子方式分流确认。
 * Stripe.js 必须从 js.stripe.com 加载（Stripe 条款禁止自托管）——这是支付供应商的必要外链，
 * 属全球可达性规范允许的「无法自托管的第三方服务」，只在支付页按需加载。
 */
import type { Stripe, StripeElements } from "@stripe/stripe-js";

let stripePromise: Promise<Stripe | null> | null = null;

/** 懒加载并缓存 Stripe 实例（publishable key 来自 checkout-info，非敏感） */
export function getStripe(publishableKey: string): Promise<Stripe | null> {
  if (!stripePromise) {
    stripePromise = import("@stripe/stripe-js").then(({ loadStripe }) =>
      loadStripe(publishableKey),
    );
  }
  return stripePromise;
}

/** 微信支付：handleActions=false 拿二维码内容，由页面本地渲染（不跳 Stripe 托管页） */
export async function startWechatPay(stripe: Stripe, clientSecret: string): Promise<string> {
  const result = await stripe.confirmWechatPayPayment(
    clientSecret,
    { payment_method_options: { wechat_pay: { client: "web" } } },
    { handleActions: false },
  );
  if (result.error) {
    throw new Error(result.error.message);
  }
  // stripe-js 的 next_action 类型不含微信二维码字段，按文档结构断言
  const nextAction = result.paymentIntent?.next_action as
    { wechat_pay_display_qr_code?: { data?: string } } | null | undefined;
  const qr = nextAction?.wechat_pay_display_qr_code?.data;
  if (!qr) {
    throw new Error("未拿到微信支付二维码，请重试");
  }
  return qr;
}

/** 支付宝：桌面端 handleActions=false 取托管页 URL 本地生成二维码；移动端整页跳转 */
export async function startAlipay(
  stripe: Stripe,
  clientSecret: string,
  returnUrl: string,
): Promise<{ qrUrl?: string }> {
  const isMobile = /Mobi|Android|iPhone/i.test(navigator.userAgent);
  if (isMobile) {
    const result = await stripe.confirmAlipayPayment(clientSecret, { return_url: returnUrl });
    if (result.error) {
      throw new Error(result.error.message);
    }
    return {};
  }
  const result = await stripe.confirmAlipayPayment(
    clientSecret,
    { return_url: returnUrl },
    { handleActions: false },
  );
  if (result.error) {
    throw new Error(result.error.message);
  }
  const nextAction = result.paymentIntent?.next_action as
    { alipay_handle_redirect?: { url?: string } } | null | undefined;
  const qrUrl = nextAction?.alipay_handle_redirect?.url;
  if (!qrUrl) {
    throw new Error("未拿到支付宝支付链接，请重试");
  }
  return { qrUrl };
}

/** 银行卡：deferred 模式初始化 Elements，只渲染 card 一种；界面固定中文 */
export function createCardElements(
  stripe: Stripe,
  opts: { amount: number; currency: string },
): StripeElements {
  return stripe.elements({
    mode: "payment",
    amount: opts.amount,
    currency: opts.currency.toLowerCase(),
    paymentMethodTypes: ["card"],
    locale: "zh",
    appearance: { theme: "stripe", variables: { borderRadius: "6px", colorPrimary: "#0fb389" } },
  });
}

/** 银行卡确认：redirect=if_required，3DS 才整页跳转，其余留在当前页 */
export async function confirmCardPayment(
  stripe: Stripe,
  elements: StripeElements,
  clientSecret: string,
  returnUrl: string,
): Promise<string> {
  const submitResult = await elements.submit();
  if (submitResult.error) {
    throw new Error(submitResult.error.message);
  }
  const result = await stripe.confirmPayment({
    elements,
    clientSecret,
    confirmParams: { return_url: returnUrl },
    redirect: "if_required",
  });
  if (result.error) {
    throw new Error(result.error.message);
  }
  return result.paymentIntent?.status ?? "processing";
}
