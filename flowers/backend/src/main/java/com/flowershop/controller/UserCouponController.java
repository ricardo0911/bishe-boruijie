package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/{userId}/coupons")
public class UserCouponController {

    @GetMapping("/available")
    public ApiResponse<List<Map<String, Object>>> listAvailableCoupons(@PathVariable Long userId) {
        // 当前项目未建立优惠券表，先提供可用券模板数据，保证结算页可用。
        List<Map<String, Object>> coupons = List.of(
            Map.of(
                "id", -101L,
                "name", "新客立减券",
                "type", "CASH",
                "value", 12,
                "minOrderAmount", 99,
                "description", "满99可用"
            ),
            Map.of(
                "id", -102L,
                "name", "鲜花满减券",
                "type", "CASH",
                "value", 25,
                "minOrderAmount", 199,
                "description", "满199可用"
            ),
            Map.of(
                "id", -103L,
                "name", "节日折扣券",
                "type", "PERCENT",
                "value", 95,
                "minOrderAmount", 0,
                "description", "95折"
            )
        );
        return ApiResponse.success(coupons);
    }
}
