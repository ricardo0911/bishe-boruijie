package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.CancelOrderRequest;
import com.flowershop.dto.ConfirmOrderRequest;
import com.flowershop.dto.CreateOrderRequest;
import com.flowershop.dto.CreateOrderResponse;
import com.flowershop.dto.OrderDetailResponse;
import com.flowershop.dto.OrderSummaryResponse;
import com.flowershop.dto.PayOrderRequest;
import com.flowershop.dto.SimpleActionResponse;
import com.flowershop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success("订单创建成功", orderService.createOrder(request));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable String orderNo) {
        return ApiResponse.success(orderService.getOrderDetail(orderNo));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<String>> listUserOrderNos(
        @PathVariable Long userId,
        @RequestParam(defaultValue = "50") Integer limit
    ) {
        return ApiResponse.success(orderService.listOrderNosByUser(userId, limit));
    }

    @GetMapping("/user/{userId}/details")
    public ApiResponse<List<OrderSummaryResponse>> listUserOrders(
        @PathVariable Long userId,
        @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ApiResponse.success(orderService.listOrderDetailsByUser(userId, limit));
    }

    @GetMapping("/all")
    public ApiResponse<List<OrderSummaryResponse>> listAllOrders(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "50") Integer limit
    ) {
        return ApiResponse.success(orderService.listAllOrders(status, limit));
    }

    @PostMapping("/{orderNo}/pay")
    public ApiResponse<SimpleActionResponse> payOrder(
        @PathVariable String orderNo,
        @Valid @RequestBody PayOrderRequest request
    ) {
        return ApiResponse.success(orderService.payOrder(orderNo, request));
    }

    @PostMapping("/{orderNo}/confirm")
    public ApiResponse<SimpleActionResponse> confirmOrder(
        @PathVariable String orderNo,
        @RequestBody(required = false) ConfirmOrderRequest request
    ) {
        return ApiResponse.success(orderService.confirmOrder(orderNo, request));
    }

    @PostMapping("/{orderNo}/complete")
    public ApiResponse<SimpleActionResponse> completeOrder(@PathVariable String orderNo) {
        return ApiResponse.success(orderService.completeOrder(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<SimpleActionResponse> cancelOrder(
        @PathVariable String orderNo,
        @RequestBody(required = false) CancelOrderRequest request
    ) {
        return ApiResponse.success(orderService.cancelOrder(orderNo, request));
    }
}
