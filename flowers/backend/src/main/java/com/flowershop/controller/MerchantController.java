package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.MerchantService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listMerchants() {
        return ApiResponse.success(merchantService.listMerchants());
    }

    @GetMapping("/public/support")
    public ApiResponse<Map<String, Object>> getPublicSupportInfo() {
        return ApiResponse.success(merchantService.getPublicSupportInfo());
    }

    @GetMapping("/public/list")
    public ApiResponse<List<Map<String, Object>>> listPublicMerchants() {
        return ApiResponse.success(merchantService.listPublicMerchants());
    }

    @PostMapping
    public ApiResponse<String> createMerchant(@RequestBody Map<String, String> body) {
        merchantService.createMerchant(
            body.get("name"),
            body.get("contactPhone"),
            body.get("email"),
            body.get("address")
        );
        return ApiResponse.success("创建成功", null);
    }

    @PutMapping("/{id}")
    public ApiResponse<String> updateMerchant(@PathVariable Long id, @RequestBody Map<String, String> body) {
        merchantService.updateMerchant(
            id,
            body.get("name"),
            body.get("contactPhone"),
            body.get("email"),
            body.get("address"),
            body.getOrDefault("status", "ACTIVE")
        );
        return ApiResponse.success("更新成功", null);
    }
}
