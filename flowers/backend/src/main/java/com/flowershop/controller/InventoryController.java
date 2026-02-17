package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.BatchStockResponse;
import com.flowershop.dto.InventoryAlertResponse;
import com.flowershop.service.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/alerts")
    public ApiResponse<List<InventoryAlertResponse>> lowStockAlerts() {
        return ApiResponse.success(inventoryService.listLowStockAlerts());
    }

    @GetMapping("/fefo/{flowerId}")
    public ApiResponse<List<BatchStockResponse>> listFefoBatches(@PathVariable Long flowerId) {
        return ApiResponse.success(inventoryService.listBatchesByFefo(flowerId));
    }

    @PostMapping("/batch")
    public ApiResponse<String> addBatch(@RequestBody Map<String, Object> body) {
        inventoryService.addBatch(
            Long.parseLong(body.get("flowerId").toString()),
            (String) body.getOrDefault("supplierName", ""),
            Integer.parseInt(body.get("quantity").toString()),
            Integer.parseInt(body.getOrDefault("shelfLifeDays", "7").toString()),
            (String) body.getOrDefault("qualityStatus", "A")
        );
        return ApiResponse.success("入库成功", null);
    }
}
