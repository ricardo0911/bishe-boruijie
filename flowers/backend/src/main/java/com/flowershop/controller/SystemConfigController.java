package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.SystemConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system-config")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listConfigs() {
        return ApiResponse.success(systemConfigService.listConfigs());
    }

    @PutMapping("/{id}")
    public ApiResponse<String> updateConfig(@PathVariable Long id, @RequestBody Map<String, String> body) {
        systemConfigService.updateConfig(id, body.get("configValue"));
        return ApiResponse.success("更新成功", null);
    }
}
