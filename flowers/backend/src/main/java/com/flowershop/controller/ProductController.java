package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.ProductDetailView;
import com.flowershop.dto.ProductRecommendView;
import com.flowershop.dto.ProductView;
import com.flowershop.exception.BusinessException;
import com.flowershop.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<List<ProductView>> listProducts(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Long categoryId
    ) {
        return ApiResponse.success(productService.listProducts(category, categoryId));
    }

    @GetMapping("/recommend/recent")
    public ApiResponse<List<ProductRecommendView>> listRecentRecommendedBouquets(
        @RequestParam(defaultValue = "30") Integer days,
        @RequestParam(defaultValue = "8") Integer limit
    ) {
        return ApiResponse.success(productService.listRecentRecommendedBouquets(days, limit));
    }

    @GetMapping("/recommended")
    public ApiResponse<List<ProductRecommendView>> listRecommendedProducts(
        @RequestParam(defaultValue = "30") Integer days,
        @RequestParam(defaultValue = "8") Integer limit
    ) {
        return ApiResponse.success(productService.listRecentRecommendedBouquets(days, limit));
    }

    @GetMapping("/hot")
    public ApiResponse<List<ProductRecommendView>> listHotProducts(
        @RequestParam(defaultValue = "30") Integer days,
        @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ApiResponse.success(productService.listRecentRecommendedBouquets(days, limit));
    }

    @GetMapping("/{productId:[0-9]+}")
    public ApiResponse<ProductDetailView> getProductDetail(@PathVariable Long productId) {
        return ApiResponse.success(productService.getProductDetail(productId));
    }

    @PostMapping
    public ApiResponse<String> createProduct(@RequestBody Map<String, Object> body) {
        productService.createProduct(
            (String) body.get("title"),
            (String) body.getOrDefault("type", "BOUQUET"),
            (String) body.get("category"),
            new BigDecimal(body.getOrDefault("basePrice", "0").toString()),
            new BigDecimal(body.getOrDefault("packagingFee", "0").toString()),
            new BigDecimal(body.getOrDefault("deliveryFee", "0").toString()),
            (String) body.get("description"),
            (String) body.get("coverImage"),
            parseBomItems(body.get("bomItems"))
        );
        return ApiResponse.success("Created successfully", null);
    }

    @PutMapping("/{productId}")
    public ApiResponse<String> updateProduct(@PathVariable Long productId, @RequestBody Map<String, Object> body) {
        productService.updateProduct(
            productId,
            (String) body.get("title"),
            (String) body.getOrDefault("type", "BOUQUET"),
            (String) body.get("category"),
            new BigDecimal(body.getOrDefault("basePrice", "0").toString()),
            new BigDecimal(body.getOrDefault("packagingFee", "0").toString()),
            new BigDecimal(body.getOrDefault("deliveryFee", "0").toString()),
            (String) body.get("description"),
            (String) body.get("coverImage"),
            (String) body.getOrDefault("status", "ON_SALE"),
            parseBomItems(body.get("bomItems"))
        );
        return ApiResponse.success("Updated successfully", null);
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<String> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ApiResponse.success("Deleted successfully", null);
    }

    private List<ProductService.BomDemand> parseBomItems(Object rawBomItems) {
        List<ProductService.BomDemand> bomItems = new ArrayList<>();
        if (!(rawBomItems instanceof List<?> rawList)) {
            return bomItems;
        }
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object flowerIdRaw = rawMap.get("flowerId");
            Object dosageRaw = rawMap.get("dosage");
            if (flowerIdRaw == null || dosageRaw == null) {
                throw new BusinessException("INVALID_BOM_ITEM", "BOM item is missing flowerId or dosage");
            }
            Long flowerId = Long.parseLong(flowerIdRaw.toString());
            BigDecimal dosage = new BigDecimal(dosageRaw.toString());
            bomItems.add(new ProductService.BomDemand(flowerId, dosage));
        }
        return bomItems;
    }
}