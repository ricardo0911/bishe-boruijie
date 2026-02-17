package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.ProductDetailView;
import com.flowershop.dto.ProductRecommendView;
import com.flowershop.dto.ProductView;
import com.flowershop.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    public ApiResponse<List<ProductView>> listProducts(@RequestParam(required = false) String category) {
        return ApiResponse.success(productService.listProducts(category));
    }

    @GetMapping("/recommend/recent")
    public ApiResponse<List<ProductRecommendView>> listRecentRecommendedBouquets(
        @RequestParam(defaultValue = "30") Integer days,
        @RequestParam(defaultValue = "8") Integer limit
    ) {
        return ApiResponse.success(productService.listRecentRecommendedBouquets(days, limit));
    }

    @GetMapping("/{productId}")
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
            (String) body.get("coverImage")
        );
        return ApiResponse.success("创建成功", null);
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
            (String) body.getOrDefault("status", "ON_SALE")
        );
        return ApiResponse.success("更新成功", null);
    }
}
