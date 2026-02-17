package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboardStats() {
        return ApiResponse.success(statsService.getDashboardStats());
    }
}
