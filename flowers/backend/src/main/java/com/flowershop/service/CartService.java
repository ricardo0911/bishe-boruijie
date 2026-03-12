package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private static final String DEFAULT_MERCHANT_DISPLAY_SQL = """
                p.merchant_account AS merchantAccount,
                COALESCE(
                    NULLIF(TRIM(aa.display_name), ''),
                    NULLIF(TRIM(p.merchant_account), ''),
                    '\u5b98\u65b9\u82b1\u5e97'
                ) AS merchantName,
            """;

    private static final String DEFAULT_MERCHANT_JOIN_SQL = """
            LEFT JOIN auth_account aa ON p.merchant_account = aa.account AND aa.role_code = 'MERCHANT'
            """;

    private final JdbcTemplate jdbcTemplate;
    private String merchantDisplaySql = DEFAULT_MERCHANT_DISPLAY_SQL;
    private String merchantJoinSql = DEFAULT_MERCHANT_JOIN_SQL;
    private String merchantGroupBySql = "p.merchant_account, aa.display_name";

    public CartService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void configureMerchantDisplayStrategy() {
        if (tableExists("merchant") && tableHasColumn("merchant", "account")) {
            merchantDisplaySql = """
                        p.merchant_account AS merchantAccount,
                        COALESCE(
                            NULLIF(TRIM(m.name), ''),
                            NULLIF(TRIM(aa.display_name), ''),
                            NULLIF(TRIM(p.merchant_account), ''),
                            (SELECT NULLIF(TRIM(mx.name), '')
                             FROM merchant mx
                             ORDER BY CASE WHEN mx.status = 'ACTIVE' THEN 0 ELSE 1 END, mx.id DESC
                             LIMIT 1),
                            '\u5b98\u65b9\u82b1\u5e97'
                        ) AS merchantName,
                    """;
            merchantJoinSql = """
                    LEFT JOIN auth_account aa ON p.merchant_account = aa.account AND aa.role_code = 'MERCHANT'
                    LEFT JOIN merchant m ON m.account = p.merchant_account
                    """;
            merchantGroupBySql = "p.merchant_account, m.name, aa.display_name";
            return;
        }

        if (tableExists("users") && tableExists("merchants")) {
            merchantDisplaySql = """
                        p.merchant_account AS merchantAccount,
                        COALESCE(
                            NULLIF(TRIM(m.shop_name), ''),
                            NULLIF(TRIM(aa.display_name), ''),
                            NULLIF(TRIM(u.nickname), ''),
                            NULLIF(TRIM(p.merchant_account), ''),
                            '\u5b98\u65b9\u82b1\u5e97'
                        ) AS merchantName,
                    """;
            merchantJoinSql = """
                    LEFT JOIN auth_account aa ON p.merchant_account = aa.account AND aa.role_code = 'MERCHANT'
                    LEFT JOIN users u ON p.merchant_account = u.username AND u.role = 'MERCHANT'
                    LEFT JOIN merchants m ON m.user_id = u.id
                    """;
            merchantGroupBySql = "p.merchant_account, m.shop_name, aa.display_name, u.nickname";
            return;
        }

        if (tableExists("merchant")) {
            merchantDisplaySql = """
                        p.merchant_account AS merchantAccount,
                        COALESCE(
                            NULLIF(TRIM(aa.display_name), ''),
                            NULLIF(TRIM(p.merchant_account), ''),
                            (SELECT NULLIF(TRIM(m.name), '')
                             FROM merchant m
                             ORDER BY CASE WHEN m.status = 'ACTIVE' THEN 0 ELSE 1 END, m.id DESC
                             LIMIT 1),
                            '\u5b98\u65b9\u82b1\u5e97'
                        ) AS merchantName,
                    """;
        } else {
            merchantDisplaySql = DEFAULT_MERCHANT_DISPLAY_SQL;
        }

        merchantJoinSql = DEFAULT_MERCHANT_JOIN_SQL;
        merchantGroupBySql = "p.merchant_account, aa.display_name";
    }

    public List<Map<String, Object>> listCartItems(Long userId) {
        configureMerchantDisplayStrategy();

        StringBuilder sql = new StringBuilder("""
            SELECT
                ci.id,
                ci.product_id AS productId,
                p.title AS productTitle,
                p.cover_image AS coverImage,
            """);
        sql.append(merchantDisplaySql);
        sql.append("""
                ci.quantity,
                CASE
                    WHEN COALESCE(SUM(f.sale_price * b.dosage), 0) > 0
                        THEN ROUND(COALESCE(SUM(f.sale_price * b.dosage), 0) + p.packaging_fee + p.delivery_fee, 2)
                    ELSE ROUND(p.base_price + p.packaging_fee + p.delivery_fee, 2)
                END AS unitPrice
            FROM cart_item ci
            JOIN product p ON ci.product_id = p.id
            """);
        sql.append(merchantJoinSql);
        sql.append("""
            LEFT JOIN product_bom b ON p.id = b.product_id
            LEFT JOIN flower_material f ON b.flower_id = f.id
            WHERE ci.user_id = ?
            GROUP BY ci.id, ci.product_id, p.title, p.cover_image,
            """);
        sql.append(merchantGroupBySql);
        sql.append(", ci.quantity, p.base_price, p.packaging_fee, p.delivery_fee ");
        sql.append(" ORDER BY ci.created_at DESC ");

        return jdbcTemplate.queryForList(sql.toString(), userId);
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
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        jdbcTemplate.update(
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

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
            """,
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }

    private boolean tableHasColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
            Integer.class,
            tableName,
            columnName
        );
        return count != null && count > 0;
    }
}
