import type { HttpClient } from "./http";
import type {
  CheckoutInfo,
  OrderCreateResponse,
  OrderResponse,
  PaymentIntentInfo,
  PlanResponse,
  VerifyOrderResponse,
} from "./types";

/** 控制台用到的全部业务接口。http 由外部传入，测试换假实现即可 */
export interface ConsoleApi {
  listPlans(): Promise<PlanResponse[]>;
  createOrder(planId: number): Promise<OrderCreateResponse>;
  listOrders(): Promise<OrderResponse[]>;
  getOrder(orderNo: string): Promise<OrderResponse>;
  cancelOrder(orderNo: string): Promise<void>;
  checkoutInfo(): Promise<CheckoutInfo>;
  createPaymentIntent(orderNo: string): Promise<PaymentIntentInfo>;
  verifyOrder(orderNo: string): Promise<VerifyOrderResponse>;
}

export function createConsoleApi(http: HttpClient): ConsoleApi {
  return {
    listPlans() {
      return http.request("/plans");
    },
    createOrder(planId) {
      return http.request("/orders", { method: "POST", body: JSON.stringify({ planId }) });
    },
    listOrders() {
      return http.request("/orders");
    },
    getOrder(orderNo) {
      return http.request(`/orders/${encodeURIComponent(orderNo)}`);
    },
    cancelOrder(orderNo) {
      return http.request(`/orders/${encodeURIComponent(orderNo)}/cancel`, { method: "POST" });
    },
    checkoutInfo() {
      return http.request("/payment/checkout-info");
    },
    createPaymentIntent(orderNo) {
      return http.request(`/payment/orders/${encodeURIComponent(orderNo)}/intent`, {
        method: "POST",
      });
    },
    verifyOrder(orderNo) {
      return http.request("/payment/orders/verify", {
        method: "POST",
        body: JSON.stringify({ orderNo }),
      });
    },
  };
}
