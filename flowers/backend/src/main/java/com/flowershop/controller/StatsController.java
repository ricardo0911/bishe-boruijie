package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.CategorySalesResponse;
import com.flowershop.dto.LowStockAlertResponse;
import com.flowershop.dto.SalesOverviewResponse;
import com.flowershop.dto.SalesTrendResponse;
import com.flowershop.dto.TopProductResponse;
import com.flowershop.rbac.Permission;
import com.flowershop.rbac.RequirePermission;
import com.flowershop.rbac.RequireRole;
import com.flowershop.rbac.Role;
import com.flowershop.service.StatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
@RequireRole({Role.MERCHANT, Role.OPERATOR, Role.SUPER_ADMIN})
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    // ===== 原有仪表板接口 =====

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboardStats() {
        return ApiResponse.success(statsService.getDashboardStats());
    }

    // ===== 销售报表接口 =====

    /**
     * 销售概览（今日/本周/本月）
     */
    @GetMapping("/sales/overview")
    public ApiResponse<SalesOverviewResponse> getSalesOverview() {
        return ApiResponse.success(statsService.getSalesOverview());
    }

    /**
     * 销售趋势分析
     * @param period 周期类型: daily(日), weekly(周), monthly(月)
     * @param startDate 开始日期 (可选)
     * @param endDate 结束日期 (可选)
     */
    @GetMapping("/sales/trend")
    public ApiResponse<SalesTrendResponse> getSalesTrend(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(statsService.getSalesTrend(period, startDate, endDate));
    }

    /**
     * 热销商品排行
     * @param limit 返回数量，默认10，最大100
     * @param startDate 开始日期 (可选)
     * @param endDate 结束日期 (可选)
     */
    @GetMapping("/products/top")
    public ApiResponse<TopProductResponse> getTopProducts(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(statsService.getTopProducts(limit, startDate, endDate));
    }

    /**
     * 品类销售分析
     * @param startDate 开始日期 (可选)
     * @param endDate 结束日期 (可选)
     */
    @GetMapping("/categories")
    public ApiResponse<CategorySalesResponse> getCategorySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(statsService.getCategorySales(startDate, endDate));
    }

    /**
     * 低库存预警
     * @param threshold 自定义预警阈值 (可选)
     */
    @GetMapping("/inventory/low-stock")
    public ApiResponse<LowStockAlertResponse> getLowStockAlerts(
            @RequestParam(required = false) Integer threshold
    ) {
        return ApiResponse.success(statsService.getLowStockAlerts(threshold));
    }
}
