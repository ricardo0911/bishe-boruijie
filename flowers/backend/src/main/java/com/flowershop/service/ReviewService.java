package com.flowershop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowershop.exception.BusinessException;
import com.flowershop.rbac.RbacContext;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReviewService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void ensureSchema() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS reviews (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                order_id BIGINT NOT NULL,
                product_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                rating INT NOT NULL,
                content TEXT DEFAULT NULL,
                images JSON DEFAULT NULL,
                reply TEXT DEFAULT NULL,
                reply_time DATETIME DEFAULT NULL,
                status TINYINT NOT NULL DEFAULT 1,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uk_reviews_order_product_user (order_id, product_id, user_id),
                KEY idx_reviews_product (product_id),
                KEY idx_reviews_user (user_id),
                KEY idx_reviews_status (status),
                KEY idx_reviews_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product reviews'
            """
        );
    }

    public void createReview(Long orderId, Long productId, Long userId, int score, String content, Object images) {
        if (score < 1 || score > 5) {
            throw new BusinessException("INVALID_SCORE", "\u8bc4\u5206\u5fc5\u987b\u5728 1-5 \u4e4b\u95f4");
        }

        Integer orderExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM customer_order WHERE id = ? AND user_id = ? AND status IN ('PAID','CONFIRMED','SHIPPED','COMPLETED')",
            Integer.class,
            orderId,
            userId
        );
        if (orderExists == null || orderExists == 0) {
            throw new BusinessException("ORDER_NOT_ELIGIBLE", "\u8ba2\u5355\u4e0d\u5b58\u5728\u6216\u5f53\u524d\u72b6\u6001\u4e0d\u5141\u8bb8\u8bc4\u4ef7");
        }

        Integer orderProductExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM order_item WHERE order_id = ? AND product_id = ?",
            Integer.class,
            orderId,
            productId
        );
        if (orderProductExists == null || orderProductExists == 0) {
            throw new BusinessException("ORDER_PRODUCT_NOT_FOUND", "\u8ba2\u5355\u4e2d\u4e0d\u5b58\u5728\u8be5\u5546\u54c1");
        }

        Integer alreadyReviewed = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM reviews WHERE order_id = ? AND product_id = ? AND user_id = ?",
            Integer.class,
            orderId,
            productId,
            userId
        );
        if (alreadyReviewed != null && alreadyReviewed > 0) {
            throw new BusinessException("ALREADY_REVIEWED", "\u8be5\u5546\u54c1\u5df2\u8bc4\u4ef7");
        }

        jdbcTemplate.update(
            "INSERT INTO reviews(order_id, product_id, user_id, rating, content, images, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 1, NOW())",
            orderId,
            productId,
            userId,
            score,
            normalizeText(content),
            serializeImages(images)
        );
    }

    public void replyReview(Long reviewId, String reply) {
        Integer existing = countAccessibleReviews(reviewId);
        if (existing == null || existing == 0) {
            throw new BusinessException("REVIEW_NOT_FOUND", "\u8bc4\u4ef7\u4e0d\u5b58\u5728");
        }

        String normalizedReply = normalizeText(reply);
        if (normalizedReply == null) {
            throw new BusinessException("INVALID_REPLY", "\u56de\u590d\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
        }

        jdbcTemplate.update(
            "UPDATE reviews SET reply = ?, reply_time = NOW() WHERE id = ?",
            normalizedReply,
            reviewId
        );
    }

    public List<Map<String, Object>> listReviewsByProduct(Long productId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT r.id,
                   r.order_id AS orderId,
                   r.product_id AS productId,
                   r.user_id AS userId,
                   COALESCE(NULLIF(TRIM(u.name), ''), CONCAT('用户', r.user_id)) AS userName,
                   r.rating AS score,
                   r.content,
                   r.images,
                   r.reply,
                   r.reply_time AS replyTime,
                   r.created_at AS createTime
            FROM reviews r
            LEFT JOIN user_customer u ON r.user_id = u.id
            WHERE r.product_id = ?
              AND COALESCE(r.status, 1) = 1
            ORDER BY r.created_at DESC
            """,
            productId
        );
        return normalizeReviewRows(rows);
    }

    public List<Map<String, Object>> listReviewsByUser(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT r.id,
                   r.order_id AS orderId,
                   o.order_no AS orderNo,
                   r.product_id AS productId,
                   p.title AS productTitle,
                   p.cover_image AS productImage,
                   r.user_id AS userId,
                   COALESCE(NULLIF(TRIM(u.name), ''), CONCAT('用户', r.user_id)) AS userName,
                   r.rating AS score,
                   r.content,
                   r.images,
                   r.reply,
                   r.reply_time AS replyTime,
                   r.created_at AS createTime
            FROM reviews r
            JOIN customer_order o ON o.id = r.order_id
            JOIN product p ON p.id = r.product_id
            LEFT JOIN user_customer u ON u.id = r.user_id
            WHERE r.user_id = ?
            ORDER BY r.created_at DESC
            """,
            userId
        );
        return normalizeReviewRows(rows);
    }

    public List<Map<String, Object>> listAllReviews() {
        List<Map<String, Object>> rows;
        if (RbacContext.isMerchant()) {
            rows = jdbcTemplate.queryForList(
                """
                SELECT r.id,
                       r.order_id AS orderId,
                       o.order_no AS orderNo,
                       r.product_id AS productId,
                       p.title AS productTitle,
                       p.cover_image AS productImage,
                       r.user_id AS userId,
                       COALESCE(NULLIF(TRIM(u.name), ''), CONCAT('用户', r.user_id)) AS userName,
                       r.rating AS score,
                       r.content,
                       r.images,
                       r.reply,
                       r.reply_time AS replyTime,
                       r.created_at AS createTime,
                       COALESCE(r.status, 1) AS status
                FROM reviews r
                JOIN customer_order o ON o.id = r.order_id
                JOIN product p ON p.id = r.product_id
                LEFT JOIN user_customer u ON u.id = r.user_id
                WHERE p.merchant_account = ?
                ORDER BY r.created_at DESC
                """,
                requireCurrentMerchantAccount()
            );
        } else {
            rows = jdbcTemplate.queryForList(
                """
                SELECT r.id,
                       r.order_id AS orderId,
                       o.order_no AS orderNo,
                       r.product_id AS productId,
                       p.title AS productTitle,
                       p.cover_image AS productImage,
                       r.user_id AS userId,
                       COALESCE(NULLIF(TRIM(u.name), ''), CONCAT('用户', r.user_id)) AS userName,
                       r.rating AS score,
                       r.content,
                       r.images,
                       r.reply,
                       r.reply_time AS replyTime,
                       r.created_at AS createTime,
                       COALESCE(r.status, 1) AS status
                FROM reviews r
                JOIN customer_order o ON o.id = r.order_id
                JOIN product p ON p.id = r.product_id
                LEFT JOIN user_customer u ON u.id = r.user_id
                ORDER BY r.created_at DESC
                """
            );
        }
        return normalizeReviewRows(rows);
    }

    public void deleteReview(Long reviewId) {
        Integer existing = countAccessibleReviews(reviewId);
        if (existing == null || existing == 0) {
            throw new BusinessException("REVIEW_NOT_FOUND", "\u8bc4\u4ef7\u4e0d\u5b58\u5728");
        }
        jdbcTemplate.update("DELETE FROM reviews WHERE id = ?", reviewId);
    }

    private Integer countAccessibleReviews(Long reviewId) {
        if (!RbacContext.isMerchant()) {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM reviews WHERE id = ?",
                Integer.class,
                reviewId
            );
        }
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM reviews r
            JOIN product p ON p.id = r.product_id
            WHERE r.id = ?
              AND p.merchant_account = ?
            """,
            Integer.class,
            reviewId,
            requireCurrentMerchantAccount()
        );
    }

    private String requireCurrentMerchantAccount() {
        String merchantAccount = RbacContext.getCurrentAccount();
        if (merchantAccount == null || merchantAccount.trim().isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "当前商家身份无效");
        }
        return merchantAccount.trim();
    }

    private List<Map<String, Object>> normalizeReviewRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalized = new LinkedHashMap<>(row);
            normalized.put("images", parseImages(row.get("images")));
            result.add(normalized);
        }
        return result;
    }

    private String serializeImages(Object images) {
        if (images == null) {
            return null;
        }
        if (images instanceof String text) {
            String normalized = normalizeText(text);
            return normalized == null ? null : normalized;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("INVALID_REVIEW_IMAGES", "\u8bc4\u4ef7\u56fe\u7247\u683c\u5f0f\u4e0d\u6b63\u786e");
        }
    }

    private List<String> parseImages(Object rawImages) {
        if (rawImages == null) {
            return List.of();
        }
        if (rawImages instanceof List<?> list) {
            return list.stream().map(item -> item == null ? "" : String.valueOf(item)).filter(text -> !text.isBlank()).toList();
        }
        String text = String.valueOf(rawImages).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(text, STRING_LIST_TYPE);
            return values == null ? List.of() : values.stream().filter(item -> item != null && !item.isBlank()).toList();
        } catch (Exception ignored) {
            return List.of(text);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}






