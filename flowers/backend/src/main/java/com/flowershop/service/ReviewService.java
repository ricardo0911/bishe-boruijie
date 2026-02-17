package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final JdbcTemplate jdbcTemplate;

    public ReviewService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createReview(Long orderId, Long userId, int score, String content, String tags) {
        if (score < 1 || score > 5) {
            throw new BusinessException("INVALID_SCORE", "评分必须在1-5之间");
        }
        Integer orderExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM customer_order WHERE id = ? AND user_id = ? AND status IN ('PAID','COMPLETED')",
            Integer.class, orderId, userId
        );
        if (orderExists == null || orderExists == 0) {
            throw new BusinessException("ORDER_NOT_ELIGIBLE", "订单不存在或状态不允许评价");
        }
        Integer alreadyReviewed = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM review WHERE order_id = ? AND user_id = ?",
            Integer.class, orderId, userId
        );
        if (alreadyReviewed != null && alreadyReviewed > 0) {
            throw new BusinessException("ALREADY_REVIEWED", "该订单已评价");
        }
        jdbcTemplate.update(
            "INSERT INTO review(order_id, user_id, score, content, tags, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
            orderId, userId, score, content, tags
        );
    }

    public List<Map<String, Object>> listReviewsByProduct(Long productId) {
        return jdbcTemplate.queryForList(
            """
            SELECT r.id, r.order_id, r.user_id, u.name AS user_name, r.score, r.content, r.tags, r.created_at
            FROM review r
            JOIN user_customer u ON r.user_id = u.id
            JOIN order_item oi ON r.order_id = oi.order_id
            WHERE oi.product_id = ?
            ORDER BY r.created_at DESC
            """,
            productId
        );
    }

    public List<Map<String, Object>> listReviewsByUser(Long userId) {
        return jdbcTemplate.queryForList(
            """
            SELECT r.id, r.order_id, r.score, r.content, r.tags, r.created_at
            FROM review r
            WHERE r.user_id = ?
            ORDER BY r.created_at DESC
            """,
            userId
        );
    }
}
