package com.flowershop.service;

import com.flowershop.dto.BomItemView;
import com.flowershop.dto.ProductDetailView;
import com.flowershop.dto.ProductRecommendView;
import com.flowershop.dto.ProductView;
import com.flowershop.exception.BusinessException;
import com.flowershop.rbac.RbacContext;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private static final String DEFAULT_MERCHANT_DISPLAY_SQL = """
              p.merchant_account,
              COALESCE(
                NULLIF(TRIM(aa.display_name), ''),
                NULLIF(TRIM(p.merchant_account), ''),
                '\u5b98\u65b9\u82b1\u5e97'
              ) AS merchant_name
            """;

    private static final String DEFAULT_MERCHANT_JOIN_SQL = """
            LEFT JOIN auth_account aa ON p.merchant_account = aa.account AND aa.role_code = 'MERCHANT'
            """;

    private String merchantDisplaySql = DEFAULT_MERCHANT_DISPLAY_SQL;
    private String merchantJoinSql = DEFAULT_MERCHANT_JOIN_SQL;
    private String merchantGroupBySql = "p.merchant_account, aa.display_name";

    private final JdbcTemplate jdbcTemplate;

    public ProductService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureOwnershipSchema() {
        Integer columnExists = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product'
              AND COLUMN_NAME = 'merchant_account'
            """,
            Integer.class
        );
        if (columnExists == null || columnExists == 0) {
            jdbcTemplate.execute("ALTER TABLE product ADD COLUMN merchant_account VARCHAR(64) NULL DEFAULT NULL COMMENT '商家账号'");
        }

        Integer indexExists = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'product'
              AND INDEX_NAME = 'idx_product_merchant_account'
            """,
            Integer.class
        );
        if (indexExists == null || indexExists == 0) {
            jdbcTemplate.execute("CREATE INDEX idx_product_merchant_account ON product(merchant_account)");
        }

        ensureLegacyMerchantAccountSchema();

        String defaultMerchantAccount = resolvePreferredMerchantAccount();
        if (defaultMerchantAccount != null && !defaultMerchantAccount.isBlank()) {
            bindLegacyMerchantAccount(defaultMerchantAccount);
            jdbcTemplate.update(
                "UPDATE product SET merchant_account = ? WHERE merchant_account IS NULL OR TRIM(merchant_account) = ''",
                defaultMerchantAccount
            );
        }

        configureMerchantDisplayStrategy();
    }

    public List<ProductView> listProducts(String category, Long categoryId) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              p.id,
              p.title,
              p.type,
              p.category,
              p.cover_image,
              p.status,
            """);
        sql.append(merchantDisplaySql);
        sql.append("""
              ,
              GROUP_CONCAT(
                CONCAT(f.name, ' x', CAST(b.dosage AS CHAR))
                ORDER BY b.id SEPARATOR ' | '
              ) AS composition_summary,
              CASE
                WHEN COALESCE(SUM(f.sale_price * b.dosage), 0) > 0
                  THEN ROUND(COALESCE(SUM(f.sale_price * b.dosage), 0) + p.packaging_fee + p.delivery_fee, 2)
                ELSE ROUND(p.base_price + p.packaging_fee + p.delivery_fee, 2)
              END AS auto_price
            FROM product p
            """);
        sql.append(merchantJoinSql);
        sql.append("""
            LEFT JOIN product_bom b ON p.id = b.product_id
            LEFT JOIN flower_material f ON b.flower_id = f.id
            WHERE 1 = 1
            """);

        String resolvedCategory = resolveCategoryFilter(category, categoryId);

        List<Object> args = new ArrayList<>();
        if (RbacContext.isMerchant()) {
            sql.append(" AND p.merchant_account = ? ");
            args.add(requireCurrentMerchantAccount());
        } else if (!RbacContext.isSuperAdmin()) {
            sql.append(" AND p.status = 'ON_SALE' ");
        }
        if (resolvedCategory != null && !resolvedCategory.isBlank()) {
            sql.append(" AND p.category = ? ");
            args.add(resolvedCategory.trim());
        }
        sql.append(" GROUP BY p.id, p.title, p.type, p.category, p.cover_image, p.status, ");
        sql.append(merchantGroupBySql);
        sql.append(", p.base_price, p.packaging_fee, p.delivery_fee ");
        sql.append(" ORDER BY p.id DESC ");

        return jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> new ProductView(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("category"),
                rs.getBigDecimal("auto_price"),
                rs.getString("cover_image"),
                rs.getString("status"),
                rs.getString("merchant_account"),
                rs.getString("merchant_name"),
                rs.getString("composition_summary")
            ),
            args.toArray()
        );
    }

    private String resolveCategoryFilter(String category, Long categoryId) {
        if (category != null && !category.isBlank()) {
            return category.trim();
        }
        if (categoryId == null) {
            return null;
        }

        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
            "SELECT code, name FROM category WHERE id = ? LIMIT 1",
            categoryId
        );
        if (categories.isEmpty()) {
            return null;
        }

        Map<String, Object> row = categories.get(0);
        Object code = row.get("code");
        if (code != null && !code.toString().trim().isEmpty()) {
            return code.toString().trim();
        }
        Object name = row.get("name");
        if (name != null && !name.toString().trim().isEmpty()) {
            return name.toString().trim();
        }
        return null;
    }

    public List<ProductRecommendView> listRecentRecommendedBouquets(Integer days, Integer limit) {
        int safeDays = days == null ? 30 : Math.max(1, Math.min(days, 180));
        int safeLimit = limit == null ? 8 : Math.max(1, Math.min(limit, 50));
        Timestamp from = Timestamp.valueOf(LocalDateTime.now().minusDays(safeDays));

        String sql = ("""
            SELECT
              p.id,
              p.title,
              p.type,
              p.category,
              p.cover_image,
              p.status,
            """ + merchantDisplaySql + """
              ,
              CASE
                WHEN COALESCE(pc.material_price, 0) > 0
                  THEN ROUND(COALESCE(pc.material_price, 0) + p.packaging_fee + p.delivery_fee, 2)
                ELSE ROUND(p.base_price + p.packaging_fee + p.delivery_fee, 2)
              END AS auto_price,
              COALESCE(rs.recent_sold, 0) AS recent_sold
            FROM product p
            """ + merchantJoinSql + """
            LEFT JOIN (
              SELECT b.product_id, COALESCE(SUM(f.sale_price * b.dosage), 0) AS material_price
              FROM product_bom b
              JOIN flower_material f ON b.flower_id = f.id
              GROUP BY b.product_id
            ) pc ON p.id = pc.product_id
            LEFT JOIN (
              SELECT oi.product_id, COALESCE(SUM(oi.quantity), 0) AS recent_sold
              FROM order_item oi
              JOIN customer_order o ON oi.order_id = o.id
              WHERE o.status IN ('PAID', 'COMPLETED')
                AND COALESCE(o.pay_time, o.created_at) >= ?
              GROUP BY oi.product_id
            ) rs ON p.id = rs.product_id
            WHERE p.status = 'ON_SALE'
              AND p.type = 'BOUQUET'
            ORDER BY recent_sold DESC, auto_price ASC, p.id DESC
            LIMIT %d
            """).formatted(safeLimit);

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new ProductRecommendView(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("category"),
                rs.getBigDecimal("auto_price"),
                rs.getString("cover_image"),
                rs.getString("status"),
                rs.getInt("recent_sold"),
                rs.getString("merchant_account"),
                rs.getString("merchant_name")
            ),
            from
        );
    }

    public ProductDetailView getProductDetail(Long productId) {
        List<ProductSnapshot> products = jdbcTemplate.query(
            """
            SELECT
              p.id,
              p.title,
              p.type,
              p.category,
              p.description,
              p.cover_image,
              p.base_price,
              p.packaging_fee,
              p.delivery_fee,
            """ + merchantDisplaySql + """
              ,
              p.status
            FROM product p
            """ + merchantJoinSql + """
            WHERE p.id = ?
            """,
            (rs, rowNum) -> new ProductSnapshot(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("category"),
                rs.getString("description"),
                rs.getString("cover_image"),
                rs.getBigDecimal("base_price"),
                rs.getBigDecimal("packaging_fee"),
                rs.getBigDecimal("delivery_fee"),
                rs.getString("merchant_account"),
                rs.getString("merchant_name"),
                rs.getString("status")
            ),
            productId
        );
        if (products.isEmpty()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "?????: " + productId);
        }

        ProductSnapshot p = products.get(0);
        List<BomItemView> bomItems = jdbcTemplate.query(
            """
            SELECT b.flower_id, f.name AS flower_name, b.dosage, f.sale_price,
                   ROUND(f.sale_price * b.dosage, 2) AS subtotal
            FROM product_bom b
            JOIN flower_material f ON b.flower_id = f.id
            WHERE b.product_id = ?
            ORDER BY b.id
            """,
            (rs, rowNum) -> new BomItemView(
                rs.getLong("flower_id"),
                rs.getString("flower_name"),
                rs.getBigDecimal("dosage"),
                rs.getBigDecimal("sale_price"),
                rs.getBigDecimal("subtotal")
            ),
            productId
        );

        BigDecimal materialSum = bomItems.stream()
            .map(BomItemView::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal autoPrice;
        if (materialSum.compareTo(BigDecimal.ZERO) > 0) {
            autoPrice = materialSum.add(p.packagingFee()).add(p.deliveryFee());
        } else {
            autoPrice = p.basePrice().add(p.packagingFee()).add(p.deliveryFee());
        }

        int stock = queryAvailableProductStock(productId);
        int sales = queryProductSalesCount(productId);
        return new ProductDetailView(
            p.id(),
            p.title(),
            p.type(),
            p.category(),
            p.description(),
            p.coverImage(),
            p.basePrice(),
            p.packagingFee(),
            p.deliveryFee(),
            autoPrice,
            autoPrice,
            p.status(),
            p.merchantAccount(),
            p.merchantName(),
            stock,
            sales,
            bomItems
        );
    }

    public ProductSnapshot getProductSnapshot(Long productId) {
        List<ProductSnapshot> products = jdbcTemplate.query(
            """
            SELECT
              p.id,
              p.title,
              p.type,
              p.category,
              p.description,
              p.cover_image,
              p.base_price,
              p.packaging_fee,
              p.delivery_fee,
            """ + merchantDisplaySql + """
              ,
              p.status
            FROM product p
            """ + merchantJoinSql + """
            WHERE p.id = ?
            """,
            (rs, rowNum) -> new ProductSnapshot(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("type"),
                rs.getString("category"),
                rs.getString("description"),
                rs.getString("cover_image"),
                rs.getBigDecimal("base_price"),
                rs.getBigDecimal("packaging_fee"),
                rs.getBigDecimal("delivery_fee"),
                rs.getString("merchant_account"),
                rs.getString("merchant_name"),
                rs.getString("status")
            ),
            productId
        );
        if (products.isEmpty()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "?????: " + productId);
        }
        ProductSnapshot snapshot = products.get(0);
        if (!"ON_SALE".equals(snapshot.status())) {
            throw new BusinessException("PRODUCT_OFF_SHELF", "?????: " + snapshot.title());
        }
        return snapshot;
    }

    public List<BomDemand> getBomDemands(Long productId) {
        return jdbcTemplate.query(
            """
            SELECT flower_id, dosage
            FROM product_bom
            WHERE product_id = ?
            """,
            (rs, rowNum) -> new BomDemand(
                rs.getLong("flower_id"),
                rs.getBigDecimal("dosage")
            ),
            productId
        );
    }

    public BigDecimal calculateAutoUnitPrice(Long productId) {
        ProductSnapshot product = getProductSnapshot(productId);
        BigDecimal materialAmount = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(f.sale_price * b.dosage), 0)
            FROM product_bom b
            JOIN flower_material f ON b.flower_id = f.id
            WHERE b.product_id = ?
            """,
            BigDecimal.class,
            productId
        );
        BigDecimal material = materialAmount == null ? BigDecimal.ZERO : materialAmount;
        if (material.compareTo(BigDecimal.ZERO) > 0) {
            return material.add(product.packagingFee()).add(product.deliveryFee());
        }
        return product.basePrice().add(product.packagingFee()).add(product.deliveryFee());
    }


    private int queryProductSalesCount(Long productId) {
        if (!tableExists("order_item") || !tableExists("customer_order")) {
            return 0;
        }
        Integer sales = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM order_item oi
            JOIN customer_order o ON oi.order_id = o.id
            WHERE oi.product_id = ?
              AND o.status IN ('PAID', 'CONFIRMED', 'COMPLETED')
            """,
            Integer.class,
            productId
        );
        return sales == null ? 0 : Math.max(sales, 0);
    }

    private int queryAvailableProductStock(Long productId) {
        if (!tableExists("inventory_batch") || !tableExists("product_bom")) {
            return 0;
        }
        Integer stock = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(CAST(MIN(stock_units) AS SIGNED), 0)
            FROM (
                SELECT FLOOR(COALESCE(SUM(GREATEST(ib.current_qty - ib.locked_qty, 0)), 0) / NULLIF(b.dosage, 0)) AS stock_units
                FROM product_bom b
                LEFT JOIN inventory_batch ib ON ib.flower_id = b.flower_id
                WHERE b.product_id = ?
                GROUP BY b.flower_id, b.dosage
            ) stock_calc
            """,
            Integer.class,
            productId
        );
        return stock == null ? 0 : Math.max(stock, 0);
    }

    @Transactional
    public void deleteProduct(Long productId) {
        assertMerchantOwnsProduct(productId);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM product WHERE id = ?",
            Integer.class,
            productId
        );
        if (count == null || count == 0) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found: " + productId);
        }

        jdbcTemplate.update("DELETE FROM user_favorite WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM cart_item WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM product_bom WHERE product_id = ?", productId);
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
    }

    @Transactional
    public void createProduct(String title, String type, String category, BigDecimal basePrice,
                              BigDecimal packagingFee, BigDecimal deliveryFee, String description, String coverImage,
                              List<BomDemand> bomItems) {
        String merchantAccount = RbacContext.isMerchant() ? requireCurrentMerchantAccount() : null;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO product(title, type, category, base_price, packaging_fee, delivery_fee,
                    description, cover_image, merchant_account, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ON_SALE', NOW(), NOW())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, title);
            ps.setString(2, type);
            ps.setString(3, category);
            ps.setBigDecimal(4, basePrice);
            ps.setBigDecimal(5, packagingFee);
            ps.setBigDecimal(6, deliveryFee);
            ps.setString(7, description);
            ps.setString(8, coverImage);
            ps.setString(9, merchantAccount);
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() == null) {
            throw new BusinessException("PRODUCT_CREATE_FAILED", "商品创建失败");
        }
        replaceProductBom(keyHolder.getKey().longValue(), bomItems);
    }

    @Transactional
    public void updateProduct(Long productId, String title, String type, String category, BigDecimal basePrice,
                              BigDecimal packagingFee, BigDecimal deliveryFee, String description, String coverImage,
                              String status, List<BomDemand> bomItems) {
        assertMerchantOwnsProduct(productId);

        int updated = jdbcTemplate.update(
            """
            UPDATE product
            SET title = ?, type = ?, category = ?, base_price = ?, packaging_fee = ?, delivery_fee = ?,
                description = ?, cover_image = ?, status = ?, updated_at = NOW()
            WHERE id = ?
            """,
            title, type, category, basePrice, packagingFee, deliveryFee, description, coverImage, status, productId
        );
        if (updated == 0) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found: " + productId);
        }
        replaceProductBom(productId, bomItems);
    }

    private void replaceProductBom(Long productId, List<BomDemand> bomItems) {
        jdbcTemplate.update("DELETE FROM product_bom WHERE product_id = ?", productId);
        Map<Long, BigDecimal> mergedBom = new LinkedHashMap<>();
        for (BomDemand item : bomItems == null ? List.<BomDemand>of() : bomItems) {
            if (item == null || item.flowerId() == null || item.dosage() == null) {
                throw new BusinessException("INVALID_BOM_ITEM", "花材配置不能为空");
            }
            if (item.dosage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVALID_BOM_ITEM", "花材用量必须大于 0");
            }
            Integer flowerExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM flower_material WHERE id = ?",
                Integer.class,
                item.flowerId()
            );
            if (flowerExists == null || flowerExists == 0) {
                throw new BusinessException("FLOWER_NOT_FOUND", "花材不存在: " + item.flowerId());
            }
            mergedBom.merge(item.flowerId(), item.dosage(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : mergedBom.entrySet()) {
            jdbcTemplate.update(
                """
                INSERT INTO product_bom(product_id, flower_id, dosage, created_at, updated_at)
                VALUES (?, ?, ?, NOW(), NOW())
                """,
                productId,
                entry.getKey(),
                entry.getValue()
            );
        }
    }

    private void assertMerchantOwnsProduct(Long productId) {
        if (!RbacContext.isMerchant()) {
            return;
        }
        String merchantAccount = requireCurrentMerchantAccount();
        Integer owned = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM product WHERE id = ? AND merchant_account = ?",
            Integer.class,
            productId,
            merchantAccount
        );
        if (owned == null || owned == 0) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found: " + productId);
        }
    }

    private String requireCurrentMerchantAccount() {
        String merchantAccount = RbacContext.getCurrentAccount();
        if (merchantAccount == null || merchantAccount.trim().isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "瑜版挸澧犻崯鍡楊啀闊偂鍞ら弮鐘虫櫏");
        }
        return merchantAccount.trim();
    }

    private void configureMerchantDisplayStrategy() {
        boolean hasUsers = tableExists("users");
        boolean hasMerchants = tableExists("merchants");
        if (hasUsers && hasMerchants) {
            merchantDisplaySql = """
                      p.merchant_account,
                      COALESCE(
                        NULLIF(TRIM(m.shop_name), ''),
                        NULLIF(TRIM(aa.display_name), ''),
                        NULLIF(TRIM(u.nickname), ''),
                        NULLIF(TRIM(p.merchant_account), ''),
                        '\u5b98\u65b9\u82b1\u5e97'
                      ) AS merchant_name
                    """;
            merchantJoinSql = """
                    LEFT JOIN auth_account aa ON p.merchant_account = aa.account AND aa.role_code = 'MERCHANT'
                    LEFT JOIN users u ON p.merchant_account = u.username AND u.role = 'MERCHANT'
                    LEFT JOIN merchants m ON m.user_id = u.id
                    """;
            merchantGroupBySql = "p.merchant_account, m.shop_name, aa.display_name, u.nickname";
            return;
        }

        if (tableExists("merchant") && tableHasColumn("merchant", "account")) {
            merchantDisplaySql = """
                      p.merchant_account,
                      COALESCE(
                        NULLIF(TRIM(m.name), ''),
                        NULLIF(TRIM(aa.display_name), ''),
                        NULLIF(TRIM(p.merchant_account), ''),
                        (SELECT NULLIF(TRIM(mx.name), '')
                         FROM merchant mx
                         ORDER BY CASE WHEN mx.status = 'ACTIVE' THEN 0 ELSE 1 END, mx.id DESC
                         LIMIT 1),
                        '\u5b98\u65b9\u82b1\u5e97'
                      ) AS merchant_name
                    """;
            merchantJoinSql = """
                    LEFT JOIN auth_account aa ON p.merchant_account = aa.account AND aa.role_code = 'MERCHANT'
                    LEFT JOIN merchant m ON m.account = p.merchant_account
                    """;
            merchantGroupBySql = "p.merchant_account, m.name, aa.display_name";
            return;
        }

        if (tableExists("merchant")) {
            merchantDisplaySql = """
                      p.merchant_account,
                      COALESCE(
                        NULLIF(TRIM(aa.display_name), ''),
                        NULLIF(TRIM(p.merchant_account), ''),
                        (SELECT NULLIF(TRIM(m.name), '')
                         FROM merchant m
                         ORDER BY CASE WHEN m.status = 'ACTIVE' THEN 0 ELSE 1 END, m.id DESC
                         LIMIT 1),
                        '\u5b98\u65b9\u82b1\u5e97'
                      ) AS merchant_name
                    """;
        } else {
            merchantDisplaySql = DEFAULT_MERCHANT_DISPLAY_SQL;
        }

        merchantJoinSql = DEFAULT_MERCHANT_JOIN_SQL;
        merchantGroupBySql = "p.merchant_account, aa.display_name";
    }

    private void ensureLegacyMerchantAccountSchema() {
        if (!tableExists("merchant")) {
            return;
        }
        if (!tableHasColumn("merchant", "account")) {
            jdbcTemplate.execute("ALTER TABLE merchant ADD COLUMN account VARCHAR(64) NULL DEFAULT NULL COMMENT '商家账号'");
        }

        Integer indexExists = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'merchant'
              AND INDEX_NAME = 'idx_merchant_account'
            """,
            Integer.class
        );
        if (indexExists == null || indexExists == 0) {
            jdbcTemplate.execute("CREATE INDEX idx_merchant_account ON merchant(account)");
        }
    }

    private String resolvePreferredMerchantAccount() {
        List<String> accountsFromSession = jdbcTemplate.query(
            """
            SELECT s.account
            FROM auth_session s
            JOIN auth_account a ON a.account = s.account
            WHERE s.role_code = 'MERCHANT'
              AND a.role_code = 'MERCHANT'
              AND a.enabled = 1
            ORDER BY s.updated_at DESC, s.id DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("account")
        );
        if (!accountsFromSession.isEmpty()) {
            return accountsFromSession.get(0);
        }

        List<String> accounts = jdbcTemplate.query(
            """
            SELECT account
            FROM auth_account
            WHERE role_code = 'MERCHANT' AND enabled = 1
            ORDER BY CASE WHEN account = 'merchant' THEN 0 ELSE 1 END, id
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("account")
        );
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    private void bindLegacyMerchantAccount(String merchantAccount) {
        if (!tableExists("merchant") || !tableHasColumn("merchant", "account")) {
            return;
        }

        Integer mapped = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM merchant WHERE account = ?",
            Integer.class,
            merchantAccount
        );
        if (mapped != null && mapped > 0) {
            return;
        }

        List<Long> merchantIds = jdbcTemplate.query(
            """
            SELECT id
            FROM merchant
            ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, id DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getLong("id")
        );
        if (merchantIds.isEmpty()) {
            return;
        }

        jdbcTemplate.update(
            "UPDATE merchant SET account = ? WHERE id = ? AND (account IS NULL OR TRIM(account) = '')",
            merchantAccount,
            merchantIds.get(0)
        );
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

    public record ProductSnapshot(
        Long id,
        String title,
        String type,
        String category,
        String description,
        String coverImage,
        BigDecimal basePrice,
        BigDecimal packagingFee,
        BigDecimal deliveryFee,
        String merchantAccount,
        String merchantName,
        String status
    ) {
    }

    public record BomDemand(Long flowerId, BigDecimal dosage) {
    }
}










