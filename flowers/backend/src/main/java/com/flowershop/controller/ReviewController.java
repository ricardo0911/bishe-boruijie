package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ApiResponse<String> createReview(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long userId = Long.valueOf(body.get("userId").toString());
        int score = Integer.parseInt(body.get("score").toString());
        String content = (String) body.get("content");
        String tags = (String) body.get("tags");
        reviewService.createReview(orderId, userId, score, content, tags);
        return ApiResponse.success("评价成功", null);
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<List<Map<String, Object>>> listByProduct(@PathVariable Long productId) {
        return ApiResponse.success(reviewService.listReviewsByProduct(productId));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<Map<String, Object>>> listByUser(@PathVariable Long userId) {
        return ApiResponse.success(reviewService.listReviewsByUser(userId));
    }
}
