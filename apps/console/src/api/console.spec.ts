import { describe, expect, it, vi } from "vitest";
import { createConsoleApi } from "./console";
import type { HttpClient } from "./http";

function capture() {
  // 显式标注签名：否则 vi.fn 从零参箭头函数推断出空元组，mock.calls 里的 c[0]/c[1] 会类型报错
  const request = vi.fn<(path: string, init?: RequestInit) => Promise<undefined>>(
    async () => undefined,
  );
  const http = { request } as unknown as HttpClient;
  return { api: createConsoleApi(http), request };
}

describe("createConsoleApi", () => {
  it("路径与方法逐条对上服务端路由，订单号经 URL 编码", async () => {
    const { api, request } = capture();
    await api.listPlans();
    await api.createOrder(11);
    await api.listOrders();
    await api.getOrder("LN1");
    await api.cancelOrder("LN1");
    await api.checkoutInfo();
    await api.createPaymentIntent("LN1");
    await api.verifyOrder("LN1");

    expect(
      request.mock.calls.map((c) => [c[0], (c[1] as RequestInit | undefined)?.method ?? "GET"]),
    ).toEqual([
      ["/plans", "GET"],
      ["/orders", "POST"],
      ["/orders", "GET"],
      ["/orders/LN1", "GET"],
      ["/orders/LN1/cancel", "POST"],
      ["/payment/checkout-info", "GET"],
      ["/payment/orders/LN1/intent", "POST"],
      ["/payment/orders/verify", "POST"],
    ]);
    expect((request.mock.calls[1][1] as RequestInit).body).toBe('{"planId":11}');
    expect((request.mock.calls[7][1] as RequestInit).body).toBe('{"orderNo":"LN1"}');
  });
});
