package ai.mintpop.lane.controller;

import ai.mintpop.lane.request.OrderCreateRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.OrderCreateResponse;
import ai.mintpop.lane.response.OrderResponse;
import ai.mintpop.lane.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户侧订单接口。登录即可访问；归属校验在服务层，别人的单一律按不存在处理 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderCreateResponse> create(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderService.create(userId, request.getPlanId()));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> list(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(orderService.listMine(userId));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponse> get(@AuthenticationPrincipal Long userId, @PathVariable String orderNo) {
        return ApiResponse.success(orderService.getMine(userId, orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal Long userId, @PathVariable String orderNo) {
        orderService.cancel(userId, orderNo);
        return ApiResponse.success();
    }
}
