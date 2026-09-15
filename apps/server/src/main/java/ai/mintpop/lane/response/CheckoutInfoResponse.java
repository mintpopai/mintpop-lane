package ai.mintpop.lane.response;

import java.util.List;

/** 收银台信息：可用支付方式（当前仅 stripe；未配置为空列表）与前端初始化 Stripe.js 的 publishable key */
public record CheckoutInfoResponse(List<String> methods, String stripePublishableKey) {
}
