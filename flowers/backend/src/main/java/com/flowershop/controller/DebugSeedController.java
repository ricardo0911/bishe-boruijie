package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.exception.BusinessException;
import com.flowershop.service.DebugSeedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/debug")
public class DebugSeedController {

    private final DebugSeedService debugSeedService;

    @Value("${app.debug-seed.enabled:false}")
    private boolean debugSeedEnabled;

    public DebugSeedController(DebugSeedService debugSeedService) {
        this.debugSeedService = debugSeedService;
    }

    @PostMapping("/seed-visual-data")
    public ApiResponse<Map<String, Object>> seedVisualData(
        @RequestParam(defaultValue = "24") Integer orderCount
    ) {
        if (!debugSeedEnabled) {
            throw new BusinessException("DEBUG_SEED_DISABLED", "调试造数接口未启用");
        }
        return ApiResponse.success(
            "视觉与销量调试数据写入完成",
            debugSeedService.seedVisualData(orderCount)
        );
    }
}
