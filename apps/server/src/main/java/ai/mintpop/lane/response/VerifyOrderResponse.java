package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.OrderStatus;

/** 订单核实结果。前端成功口径：status 为 PAID */
public record VerifyOrderResponse(String orderNo, OrderStatus status) {
}
