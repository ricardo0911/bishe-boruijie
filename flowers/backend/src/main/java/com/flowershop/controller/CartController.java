package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.CartService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{userId}")
    public ApiResponse<List<Map<String, Object>>> listCart(@PathVariable Long userId) {
        return ApiResponse.success(cartService.listCartItems(userId));
    }

    @PostMapping
    public ApiResponse<String> addOrUpdate(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long productId = Long.valueOf(body.get("productId").toString());
        int quantity = Integer.parseInt(body.get("quantity").toString());
        cartService.addOrUpdate(userId, productId, quantity);
        return ApiResponse.success("操作成功", null);
    }

    @DeleteMapping("/{userId}/{productId}")
    public ApiResponse<String> removeItem(@PathVariable Long userId, @PathVariable Long productId) {
        cartService.removeItem(userId, productId);
        return ApiResponse.success("删除成功", null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<String> clearCart(@PathVariable Long userId) {
        cartService.clearCart(userId);
        return ApiResponse.success("清空成功", null);
    }
}
