package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserFavoriteService {

    private final JdbcTemplate jdbcTemplate;

    public UserFavoriteService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS user_favorite (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                user_id BIGINT NOT NULL,
                product_id BIGINT NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uk_user_favorite (user_id, product_id),
                CONSTRAINT fk_user_favorite_user FOREIGN KEY (user_id) REFERENCES user_customer(id),
                CONSTRAINT fk_user_favorite_product FOREIGN KEY (product_id) REFERENCES product(id),
                INDEX idx_user_favorite_user (user_id),
                INDEX idx_user_favorite_product (product_id)
            )
            """
        );
    }

    public List<Map<String, Object>> listFavorites(Long userId) {
        ensureUserExists(userId);
        return jdbcTemplate.queryForList(
            """
            SELECT uf.product_id AS productId,
                   p.id,
                   p.title,
                   p.cover_image AS coverImage,
                   uf.created_at AS createdAt,
                   uf.updated_at AS updatedAt
            FROM user_favorite uf
            LEFT JOIN product p ON p.id = uf.product_id
            WHERE uf.user_id = ?
            ORDER BY uf.updated_at DESC, uf.id DESC
            """,
            userId
        );
    }

    @Transactional
    public void addFavorite(Long userId, Long productId) {
        ensureUserExists(userId);
        ensureProductExists(productId);

        jdbcTemplate.update(
            """
            INSERT INTO user_favorite(user_id, product_id, created_at, updated_at)
            VALUES (?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE updated_at = NOW()
            """,
            userId,
            productId
        );
    }

    @Transactional
    public void removeFavorite(Long userId, Long productId) {
        ensureUserExists(userId);
        jdbcTemplate.update(
            "DELETE FROM user_favorite WHERE user_id = ? AND product_id = ?",
            userId,
            productId
        );
    }

    private void ensureUserExists(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_customer WHERE id = ?",
            Integer.class,
            userId
        );
        if (count == null || count == 0) {
            throw new BusinessException("USER_NOT_FOUND", "\u7528\u6237\u4e0d\u5b58\u5728: " + userId);
        }
    }

    private void ensureProductExists(Long productId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM product WHERE id = ?",
            Integer.class,
            productId
        );
        if (count == null || count == 0) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found: " + productId);
        }
    }
}
