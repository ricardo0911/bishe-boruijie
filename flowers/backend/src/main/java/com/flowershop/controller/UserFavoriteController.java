package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.exception.BusinessException;
import com.flowershop.service.UserFavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/{userId}/favorites")
public class UserFavoriteController {

    private final UserFavoriteService userFavoriteService;

    public UserFavoriteController(UserFavoriteService userFavoriteService) {
        this.userFavoriteService = userFavoriteService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listFavorites(@PathVariable Long userId) {
        return ApiResponse.success(userFavoriteService.listFavorites(userId));
    }

    @PostMapping
    public ApiResponse<Void> addFavorite(
        @PathVariable Long userId,
        @RequestBody Map<String, Object> body
    ) {
        userFavoriteService.addFavorite(userId, parseProductId(body));
        return ApiResponse.success("Favorite added", null);
    }

    // Keep compatibility with the miniapp endpoint currently in use.
    @PostMapping("/remove")
    public ApiResponse<Void> removeFavoriteByBody(
        @PathVariable Long userId,
        @RequestBody Map<String, Object> body
    ) {
        userFavoriteService.removeFavorite(userId, parseProductId(body));
        return ApiResponse.success("Favorite removed", null);
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> removeFavorite(
        @PathVariable Long userId,
        @PathVariable Long productId
    ) {
        userFavoriteService.removeFavorite(userId, productId);
        return ApiResponse.success("Favorite removed", null);
    }

    private static Long parseProductId(Map<String, Object> body) {
        Object value = body == null ? null : body.get("productId");
        if (value == null) {
            throw new BusinessException("VALIDATION_ERROR", "productId is required");
        }
        try {
            long productId = Long.parseLong(String.valueOf(value));
            if (productId <= 0) {
                throw new BusinessException("VALIDATION_ERROR", "productId must be positive");
            }
            return productId;
        } catch (NumberFormatException ex) {
            throw new BusinessException("VALIDATION_ERROR", "productId format invalid");
        }
    }
}
