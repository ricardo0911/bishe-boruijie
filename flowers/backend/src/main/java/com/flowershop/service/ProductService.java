package com.flowershop.service;

import com.flowershop.dto.BomItemView;
import com.flowershop.dto.ProductDetailView;
import com.flowershop.dto.ProductRecommendView;
import com.flowershop.dto.ProductView;
import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final JdbcTemplate jdbcTemplate;

    public ProductService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductView> listProducts(String category) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              p.id,
              p.title,
              p.type,
              p.category,
              p.cover_image,
              p.status,
              CASE
                WHEN COALESCE(SUM(f.sale_price * b.dosage), 0) > 0
                  THEN ROUND(COALESCE(SUM(f.sale_price * b.dosage), 0) + p.packaging_fee + p.delivery_fee, 2)
                ELSE ROUND(p.base_price + p.packaging_fee + p.delivery_fee, 2)
              END AS auto_price
            FROM product p
            LEFT JOIN product_bom b ON p.id = b.product_id
            LEFT JOIN flower_material f ON b.flower_id = f.id
            WHERE p.status = 'ON_SALE'
            """);

        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            sql.append(" AND p.category = ? ");
            args.add(category.trim());
        }
        sql.append(" GROUP BY p.id, p.title, p.type, p.category, p.cover_image, p.status, p.base_price, p.packaging_fee, p.delivery_fee ");
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
                rs.getString("status")
            ),
            args.toArray()
        );
    }

    public List<ProductRecommendView> listRecentRecommendedBouquets(Integer days, Integer limit) {
        int safeDays = days == null ? 30 : Math.max(1, Math.min(days, 180));
        int safeLimit = limit == null ? 8 : Math.max(1, Math.min(limit, 50));
        Timestamp from = Timestamp.valueOf(LocalDateTime.now().minusDays(safeDays));

        String sql = """
            SELECT
              p.id,
              p.title,
              p.type,
              p.category,
              p.cover_image,
              p.status,
              CASE
                WHEN COALESCE(pc.material_price, 0) > 0
                  THEN ROUND(COALESCE(pc.material_price, 0) + p.packaging_fee + p.delivery_fee, 2)
                ELSE ROUND(p.base_price + p.packaging_fee + p.delivery_fee, 2)
              END AS auto_price,
              COALESCE(rs.recent_sold, 0) AS recent_sold
            FROM product p
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
            """.formatted(safeLimit);

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
                rs.getInt("recent_sold")
            ),
            from
        );
    }

    public ProductDetailView getProductDetail(Long productId) {
        List<ProductSnapshot> products = jdbcTemplate.query(
            """
            SELECT id, title, type, category, description, cover_image, base_price, packaging_fee, delivery_fee, status
            FROM product
            WHERE id = ?
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
                rs.getString("status")
            ),
            productId
        );
        if (products.isEmpty()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在: " + productId);
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
            bomItems
        );
    }

    public ProductSnapshot getProductSnapshot(Long productId) {
        List<ProductSnapshot> products = jdbcTemplate.query(
            """
            SELECT id, title, type, category, description, cover_image, base_price, packaging_fee, delivery_fee, status
            FROM product
            WHERE id = ?
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
                rs.getString("status")
            ),
            productId
        );
        if (products.isEmpty()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在: " + productId);
        }
        ProductSnapshot snapshot = products.get(0);
        if (!"ON_SALE".equals(snapshot.status())) {
            throw new BusinessException("PRODUCT_OFF_SHELF", "商品已下架: " + snapshot.title());
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

    public void createProduct(String title, String type, String category, BigDecimal basePrice,
                              BigDecimal packagingFee, BigDecimal deliveryFee, String description, String coverImage) {
        jdbcTemplate.update(
            """
            INSERT INTO product(title, type, category, base_price, packaging_fee, delivery_fee,
                description, cover_image, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ON_SALE', NOW(), NOW())
            """,
            title, type, category, basePrice, packagingFee, deliveryFee, description, coverImage
        );
    }

    public void updateProduct(Long productId, String title, String type, String category, BigDecimal basePrice,
                              BigDecimal packagingFee, BigDecimal deliveryFee, String description, String coverImage,
                              String status) {
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
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在: " + productId);
        }
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
        String status
    ) {
    }

    public record BomDemand(Long flowerId, BigDecimal dosage) {
    }
}
