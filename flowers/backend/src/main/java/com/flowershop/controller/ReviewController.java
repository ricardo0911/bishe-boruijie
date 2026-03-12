package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.rbac.RequireRole;
import com.flowershop.rbac.Role;
import com.flowershop.service.ReviewService;
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
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ApiResponse<String> createReview(@RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("orderId"));
        Long productId = toLong(body.get("productId"));
        Long userId = toLong(body.get("userId"));
        int score = toInt(body.getOrDefault("score", body.get("rating")));
        String content = body.get("content") == null ? null : String.valueOf(body.get("content"));
        Object images = body.get("images");
        reviewService.createReview(orderId, productId, userId, score, content, images);
        return ApiResponse.success("\u8bc4\u4ef7\u6210\u529f", null);
    }

    @PostMapping("/{reviewId}/reply")
    public ApiResponse<String> replyReview(@PathVariable Long reviewId, @RequestBody Map<String, Object> body) {
        String reply = body.get("reply") == null ? null : String.valueOf(body.get("reply"));
        reviewService.replyReview(reviewId, reply);
        return ApiResponse.success("\u56de\u590d\u6210\u529f", null);
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAll() {
        return ApiResponse.success(reviewService.listAllReviews());
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<List<Map<String, Object>>> listByProduct(@PathVariable Long productId) {
        return ApiResponse.success(reviewService.listReviewsByProduct(productId));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<Map<String, Object>>> listByUser(@PathVariable Long userId) {
        return ApiResponse.success(reviewService.listReviewsByUser(userId));
    }

    @RequireRole(Role.SUPER_ADMIN)
    @DeleteMapping("/{reviewId}")
    public ApiResponse<String> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ApiResponse.success("\u5220\u9664\u6210\u529f", null);
    }

    private static Long toLong(Object value) {
        return Long.valueOf(String.valueOf(value));
    }

    private static int toInt(Object value) {
        return Integer.parseInt(String.valueOf(value));
    }
}
