package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listEnabled() {
        return ApiResponse.success(categoryService.listEnabled());
    }

    @GetMapping("/all")
    public ApiResponse<List<Map<String, Object>>> listAll() {
        return ApiResponse.success(categoryService.listAll());
    }
}
