import { describe, expect, it } from "vitest";
import { buildPayOptions, isPaidStatus, STRIPE_SUB_METHODS } from "./payment";

describe("payment", () => {
  it("展示顺序固定：微信 → 支付宝 → 银行卡", () => {
    expect(STRIPE_SUB_METHODS).toEqual(["wxpay", "alipay", "card"]);
  });

  it("只有 stripe 通道时拍平成三张卡；没有通道时为空", () => {
    expect(buildPayOptions(["stripe"]).map((o) => o.key)).toEqual([
      "stripe:wxpay",
      "stripe:alipay",
      "stripe:card",
    ]);
    expect(buildPayOptions([])).toEqual([]);
  });

  it("成功口径只认 PAID：控制台的订单没有 COMPLETED 这一档", () => {
    expect(isPaidStatus("PAID")).toBe(true);
    expect(isPaidStatus("COMPLETED")).toBe(false);
    expect(isPaidStatus("PENDING")).toBe(false);
  });
});
