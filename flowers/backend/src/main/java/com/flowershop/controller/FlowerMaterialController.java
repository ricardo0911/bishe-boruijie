package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.FlowerMaterialService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/flowers")
public class FlowerMaterialController {

    private final FlowerMaterialService flowerMaterialService;

    public FlowerMaterialController(FlowerMaterialService flowerMaterialService) {
        this.flowerMaterialService = flowerMaterialService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAll(@RequestParam(required = false) String category) {
        return ApiResponse.success(flowerMaterialService.listAll(category));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable Long id) {
        return ApiResponse.success(flowerMaterialService.getById(id));
    }

    @PostMapping
    public ApiResponse<String> create(@RequestBody Map<String, Object> body) {
        flowerMaterialService.create(
            (String) body.get("name"),
            (String) body.get("category"),
            (String) body.getOrDefault("unit", "stem"),
            new BigDecimal(body.get("salePrice").toString()),
            new BigDecimal(body.get("costPrice").toString()),
            Integer.parseInt(body.getOrDefault("shelfLifeDays", "7").toString()),
            new BigDecimal(body.getOrDefault("warnThreshold", "10").toString()),
            (String) body.get("image")
        );
        return ApiResponse.success("创建成功", null);
    }

    @PutMapping("/{id}")
    public ApiResponse<String> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        flowerMaterialService.update(
            id,
            (String) body.get("name"),
            (String) body.get("category"),
            (String) body.getOrDefault("unit", "stem"),
            new BigDecimal(body.get("salePrice").toString()),
            new BigDecimal(body.get("costPrice").toString()),
            Integer.parseInt(body.getOrDefault("shelfLifeDays", "7").toString()),
            new BigDecimal(body.getOrDefault("warnThreshold", "10").toString()),
            Boolean.parseBoolean(body.getOrDefault("enabled", "true").toString()),
            (String) body.get("image")
        );
        return ApiResponse.success("更新成功", null);
    }
}
