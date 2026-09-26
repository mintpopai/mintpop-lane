import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Stripe, StripeElements } from "@stripe/stripe-js";
import {
  confirmCardPayment,
  createCardElements,
  getStripe,
  startAlipay,
  startWechatPay,
} from "./stripe";

const loadStripeMock = vi.fn();
vi.mock("@stripe/stripe-js", () => ({
  loadStripe: (...args: unknown[]) => loadStripeMock(...args),
}));

function fakeStripe(overrides: Partial<Stripe>): Stripe {
  return overrides as Stripe;
}

const RETURN_URL = "http://localhost:6203/payment/result?order_no=LN1";

afterEach(() => vi.restoreAllMocks());

describe("getStripe", () => {
  it("缓存实例：多次调用只加载一次 SDK，语言固定中文", async () => {
    const instance = fakeStripe({});
    loadStripeMock.mockResolvedValue(instance);
    expect(await getStripe("pk_1")).toBe(instance);
    expect(await getStripe("pk_2")).toBe(instance);
    expect(loadStripeMock).toHaveBeenCalledOnce();
    expect(loadStripeMock).toHaveBeenCalledWith("pk_1", { locale: "zh" });
  });
});

describe("startWechatPay", () => {
  it("handleActions=false 拿二维码内容本地渲染", async () => {
    const confirm = vi.fn().mockResolvedValue({
      paymentIntent: { next_action: { wechat_pay_display_qr_code: { data: "weixin://wxpay/x" } } },
    });
    await expect(
      startWechatPay(fakeStripe({ confirmWechatPayPayment: confirm }), "cs"),
    ).resolves.toBe("weixin://wxpay/x");
    expect(confirm).toHaveBeenCalledWith(
      "cs",
      { payment_method_options: { wechat_pay: { client: "web" } } },
      { handleActions: false },
    );
  });

  it("Stripe 报错或缺二维码都抛错", async () => {
    await expect(
      startWechatPay(
        fakeStripe({
          confirmWechatPayPayment: vi.fn().mockResolvedValue({ error: { message: "被拒" } }),
        }),
        "cs",
      ),
    ).rejects.toThrow("被拒");
    await expect(
      startWechatPay(
        fakeStripe({
          confirmWechatPayPayment: vi
            .fn()
            .mockResolvedValue({ paymentIntent: { next_action: null } }),
        }),
        "cs",
      ),
    ).rejects.toThrow("未拿到微信支付二维码，请重试");
  });

  it("Stripe 报错缺 message 时用兜底文案", async () => {
    await expect(
      startWechatPay(
        fakeStripe({
          confirmWechatPayPayment: vi.fn().mockResolvedValue({ error: {} }),
        }),
        "cs",
      ),
    ).rejects.toThrow("支付请求被拒绝，请重试");
  });
});

describe("startAlipay", () => {
  const DESKTOP = "Mozilla/5.0 (Macintosh) Chrome/140";
  const MOBILE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Mobile/15E148";

  it("桌面端 handleActions=false 取托管页 URL 本地生成二维码", async () => {
    vi.spyOn(navigator, "userAgent", "get").mockReturnValue(DESKTOP);
    const confirm = vi.fn().mockResolvedValue({
      paymentIntent: { next_action: { alipay_handle_redirect: { url: "https://alipay/x" } } },
    });
    await expect(
      startAlipay(fakeStripe({ confirmAlipayPayment: confirm }), "cs", RETURN_URL),
    ).resolves.toEqual({ qrUrl: "https://alipay/x" });
    expect(confirm).toHaveBeenCalledWith(
      "cs",
      { return_url: RETURN_URL },
      { handleActions: false },
    );
  });

  it("移动端交给 Stripe 整页跳转", async () => {
    vi.spyOn(navigator, "userAgent", "get").mockReturnValue(MOBILE);
    const confirm = vi.fn().mockResolvedValue({});
    await expect(
      startAlipay(fakeStripe({ confirmAlipayPayment: confirm }), "cs", RETURN_URL),
    ).resolves.toEqual({});
    expect(confirm).toHaveBeenCalledWith("cs", { return_url: RETURN_URL });
  });
});

describe("createCardElements / confirmCardPayment", () => {
  let elements: StripeElements;
  let submit: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    submit = vi.fn().mockResolvedValue({});
    elements = { submit } as unknown as StripeElements;
  });

  it("deferred 模式只渲染 card，币种转小写，语言固定中文", () => {
    const elementsFactory = vi.fn().mockReturnValue({} as StripeElements);
    createCardElements(fakeStripe({ elements: elementsFactory }), {
      amount: 9999,
      currency: "USD",
    });
    expect(elementsFactory).toHaveBeenCalledWith(
      expect.objectContaining({
        mode: "payment",
        amount: 9999,
        currency: "usd",
        paymentMethodTypes: ["card"],
        locale: "zh",
      }),
    );
  });

  it("先提交表单再确认，redirect=if_required；表单不过不发起确认", async () => {
    const confirmPayment = vi.fn().mockResolvedValue({ paymentIntent: { status: "succeeded" } });
    await expect(
      confirmCardPayment(fakeStripe({ confirmPayment }), elements, "cs", RETURN_URL),
    ).resolves.toBe("succeeded");
    expect(confirmPayment).toHaveBeenCalledWith(
      expect.objectContaining({ clientSecret: "cs", redirect: "if_required" }),
    );

    submit.mockResolvedValue({ error: { message: "卡号无效" } });
    const untouched = vi.fn();
    await expect(
      confirmCardPayment(fakeStripe({ confirmPayment: untouched }), elements, "cs", RETURN_URL),
    ).rejects.toThrow("卡号无效");
    expect(untouched).not.toHaveBeenCalled();
  });
});
