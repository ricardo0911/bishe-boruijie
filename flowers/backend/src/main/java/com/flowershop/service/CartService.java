package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private final JdbcTemplate jdbcTemplate;

    public CartService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listCartItems(Long userId) {
        return jdbcTemplate.queryForList(
            """
            SELECT
                ci.id,
                ci.product_id AS productId,
                p.title AS productTitle,
                p.cover_image AS coverImage,
                ci.quantity,
                CASE
                    WHEN COALESCE(SUM(f.sale_price * b.dosage), 0) > 0
                        THEN ROUND(COALESCE(SUM(f.sale_price * b.dosage), 0) + p.packaging_fee + p.delivery_fee, 2)
                    ELSE ROUND(p.base_price + p.packaging_fee + p.delivery_fee, 2)
                END AS unitPrice
            FROM cart_item ci
            JOIN product p ON ci.product_id = p.id
            LEFT JOIN product_bom b ON p.id = b.product_id
            LEFT JOIN flower_material f ON b.flower_id = f.id
            WHERE ci.user_id = ?
            GROUP BY ci.id, ci.product_id, p.title, p.cover_image, ci.quantity, p.base_price, p.packaging_fee, p.delivery_fee
            ORDER BY ci.created_at DESC
            """,
            userId
        );
    }

    @Transactional
    public void addOrUpdate(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "数量必须大于0");
        }
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM product WHERE id = ? AND status = 'ON_SALE'",
            Integer.class, productId
        );
        if (exists == null || exists == 0) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在或已下架");
        }

        int updated = jdbcTemplate.update(
            """
            INSERT INTO cart_item(user_id, product_id, quantity, created_at, updated_at)
            VALUES (?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE quantity = ?, updated_at = NOW()
            """,
            userId, productId, quantity, quantity
        );
    }

    public void removeItem(Long userId, Long productId) {
        jdbcTemplate.update(
            "DELETE FROM cart_item WHERE user_id = ? AND product_id = ?",
            userId, productId
        );
    }

    public void clearCart(Long userId) {
        jdbcTemplate.update("DELETE FROM cart_item WHERE user_id = ?", userId);
    }
}
