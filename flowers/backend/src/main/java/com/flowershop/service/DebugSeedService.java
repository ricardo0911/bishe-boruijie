package com.flowershop.service;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DebugSeedService {

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final ProductService productService;

    public DebugSeedService(JdbcTemplate jdbcTemplate, ProductService productService) {
        this.jdbcTemplate = jdbcTemplate;
        this.productService = productService;
    }

    @Transactional
    public Map<String, Object> seedVisualData(Integer orderCount) {
        int safeOrderCount = orderCount == null ? 24 : Math.max(6, Math.min(orderCount, 200));

        ensureFlowerImageColumn();

        int flowerImageUpdates = 0;
        flowerImageUpdates += updateFlowerImage("红玫瑰", "/images/materials/rose.svg");
        flowerImageUpdates += updateFlowerImage("满天星", "/images/materials/gypsophila.svg");
        flowerImageUpdates += updateFlowerImage("白百合", "/images/materials/lily.svg");
        flowerImageUpdates += updateFlowerImage("康乃馨", "/images/materials/carnation.svg");
        flowerImageUpdates += updateFlowerImage("包装纸", "/images/materials/wrap.svg");
        flowerImageUpdates += updateFlowerImage("丝带", "/images/materials/ribbon.svg");
        flowerImageUpdates += normalizeLegacyFlowerImagePaths();

        List<ProductSeed> productSeeds = buildProductSeeds();
        int productUpserts = 0;
        int bomUpserts = 0;
        List<Long> seededProductIds = new ArrayList<>();
        for (ProductSeed seed : productSeeds) {
            Long productId = upsertProduct(seed);
            productUpserts++;
            seededProductIds.add(productId);
            for (BomSeed bom : seed.bom()) {
                bomUpserts += upsertBom(productId, bom);
            }
        }
        productUpserts += normalizeLegacyProductImagePaths();

        List<Long> userIds = loadUserIds();
        if (userIds.isEmpty()) {
            Long userId = createFallbackUser();
            userIds.add(userId);
        }

        int ordersCreated = 0;
        int paidOrders = 0;
        int completedOrders = 0;
        int cancelledOrders = 0;
        int refundedOrders = 0;
        int lockedOrders = 0;

        for (int i = 0; i < safeOrderCount; i++) {
            Long userId = userIds.get(i % userIds.size());
            Long productId = seededProductIds.get(i % seededProductIds.size());
            int quantity = ThreadLocalRandom.current().nextInt(1, 4);
            BigDecimal unitPrice = productService.calculateAutoUnitPrice(productId);
            BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

            String status;
            LocalDateTime createdAt = LocalDateTime.now().minusDays(ThreadLocalRandom.current().nextInt(1, 25));
            LocalDateTime payTime = null;
            LocalDateTime cancelTime = null;
            LocalDateTime lockExpireAt = null;
            BigDecimal paymentAmount;
            String paymentChannel = null;
            String paymentNo = null;
            String remark;

            int mod = i % 6;
            if (mod == 0) {
                status = "COMPLETED";
                payTime = createdAt.plusHours(2);
                paymentAmount = totalAmount;
                paymentChannel = "MOCK_WECHAT";
                paymentNo = "MOCK_" + System.currentTimeMillis() + i;
                remark = "debug seed completed";
                completedOrders++;
            } else if (mod == 1 || mod == 2 || mod == 3) {
                status = "PAID";
                payTime = createdAt.plusHours(1);
                paymentAmount = totalAmount;
                paymentChannel = "MOCK_WECHAT";
                paymentNo = "MOCK_" + System.currentTimeMillis() + i;
                remark = "debug seed paid";
                paidOrders++;
            } else if (mod == 4) {
                status = "CANCELLED";
                paymentAmount = BigDecimal.ZERO;
                cancelTime = createdAt.plusHours(1);
                remark = "debug seed cancelled";
                cancelledOrders++;
            } else {
                status = "REFUNDED";
                payTime = createdAt.plusHours(1);
                cancelTime = createdAt.plusHours(12);
                paymentAmount = totalAmount;
                paymentChannel = "MOCK_WECHAT";
                paymentNo = "MOCK_" + System.currentTimeMillis() + i;
                remark = "debug seed refunded";
                refundedOrders++;
            }

            if (i % 7 == 0) {
                status = "LOCKED";
                payTime = null;
                cancelTime = null;
                paymentAmount = totalAmount;
                paymentChannel = null;
                paymentNo = null;
                lockExpireAt = LocalDateTime.now().plusMinutes(30);
                remark = "debug seed locked";
                lockedOrders++;
            }

            Long orderId = insertOrder(
                generateOrderNo(),
                userId,
                totalAmount,
                paymentAmount,
                status,
                paymentChannel,
                paymentNo,
                payTime,
                cancelTime,
                lockExpireAt,
                remark,
                createdAt
            );

            String title = jdbcTemplate.queryForObject(
                "SELECT title FROM product WHERE id = ?",
                String.class,
                productId
            );

            jdbcTemplate.update(
                """
                INSERT INTO order_item(order_id, product_id, product_title, unit_price, quantity, line_amount, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                productId,
                title,
                unitPrice,
                quantity,
                totalAmount,
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt)
            );
            ordersCreated++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flowerImageUpdates", flowerImageUpdates);
        result.put("productUpserts", productUpserts);
        result.put("bomUpserts", bomUpserts);
        result.put("ordersCreated", ordersCreated);
        result.put("paidOrders", paidOrders);
        result.put("completedOrders", completedOrders);
        result.put("cancelledOrders", cancelledOrders);
        result.put("refundedOrders", refundedOrders);
        result.put("lockedOrders", lockedOrders);
        result.put("seededUsers", userIds.size());
        return result;
    }

    private int normalizeLegacyFlowerImagePaths() {
        int updated = 0;
        updated += jdbcTemplate.update("UPDATE flower_material SET image_url = '/images/materials/rose.svg' WHERE image_url LIKE '%text=Rose%'");
        updated += jdbcTemplate.update("UPDATE flower_material SET image_url = '/images/materials/gypsophila.svg' WHERE image_url LIKE '%text=Gypsophila%'");
        updated += jdbcTemplate.update("UPDATE flower_material SET image_url = '/images/materials/lily.svg' WHERE image_url LIKE '%text=Lily%'");
        updated += jdbcTemplate.update("UPDATE flower_material SET image_url = '/images/materials/carnation.svg' WHERE image_url LIKE '%text=Carnation%'");
        updated += jdbcTemplate.update("UPDATE flower_material SET image_url = '/images/materials/wrap.svg' WHERE image_url LIKE '%text=Wrap%'");
        updated += jdbcTemplate.update("UPDATE flower_material SET image_url = '/images/materials/ribbon.svg' WHERE image_url LIKE '%text=Ribbon%'");
        return updated;
    }

    private int normalizeLegacyProductImagePaths() {
        int updated = 0;
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/rose-black-baccara.jpg' WHERE cover_image LIKE '%Rose+11%'");
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/rose-amour.jpg' WHERE cover_image LIKE '%Lily+Fresh%'");
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/rose-zhenai.jpg' WHERE cover_image LIKE '%Carnation+Care%'");
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/rose-explorer.jpg' WHERE cover_image LIKE '%French+Rose%'");
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/rose-freedom.jpg' WHERE cover_image LIKE '%Sunflower+Joy%'");
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/rose-lisi.jpg' WHERE cover_image LIKE '%Business+Green%'");
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/rose-baoliandeng.jpg' WHERE cover_image LIKE '%Pink+Love%'");
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/rose-carola.jpg' WHERE cover_image LIKE '%Warm+Carnation%'");
        updated += jdbcTemplate.update("UPDATE product SET cover_image = '/images/products/custom/bouquet-bud-box.jpg' WHERE cover_image = '/images/products/test.svg'");
        return updated;
    }

    private void ensureFlowerImageColumn() {
        Integer exists = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'flower_material'
              AND COLUMN_NAME = 'image_url'
            """,
            Integer.class
        );
        if (exists == null || exists == 0) {
            jdbcTemplate.execute("ALTER TABLE flower_material ADD COLUMN image_url VARCHAR(255) NULL AFTER warn_threshold");
        }
    }

    private int updateFlowerImage(String name, String imageUrl) {
        return jdbcTemplate.update(
            "UPDATE flower_material SET image_url = ?, updated_at = NOW() WHERE name = ?",
            imageUrl,
            name
        );
    }

    private Long upsertProduct(ProductSeed seed) {
        List<Long> ids = jdbcTemplate.query(
            "SELECT id FROM product WHERE title = ? LIMIT 1",
            (rs, rowNum) -> rs.getLong("id"),
            seed.title()
        );

        if (!ids.isEmpty()) {
            Long id = ids.get(0);
            jdbcTemplate.update(
                """
                UPDATE product
                SET category = ?,
                    packaging_fee = ?,
                    delivery_fee = ?,
                    description = ?,
                    cover_image = ?,
                    status = 'ON_SALE',
                    updated_at = NOW()
                WHERE id = ?
                """,
                seed.category(),
                seed.packagingFee(),
                seed.deliveryFee(),
                seed.description(),
                seed.coverImage(),
                id
            );
            return id;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO product(title, type, category, base_price, packaging_fee, delivery_fee, description, cover_image, status, created_at, updated_at)
                VALUES (?, 'BOUQUET', ?, 0, ?, ?, ?, ?, 'ON_SALE', NOW(), NOW())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, seed.title());
            ps.setString(2, seed.category());
            ps.setBigDecimal(3, seed.packagingFee());
            ps.setBigDecimal(4, seed.deliveryFee());
            ps.setString(5, seed.description());
            ps.setString(6, seed.coverImage());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private int upsertBom(Long productId, BomSeed bomSeed) {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM product_bom WHERE product_id = ? AND flower_id = ?",
            Integer.class,
            productId,
            bomSeed.flowerId()
        );

        if (exists != null && exists > 0) {
            return jdbcTemplate.update(
                "UPDATE product_bom SET dosage = ?, loss_rate = ?, updated_at = NOW() WHERE product_id = ? AND flower_id = ?",
                bomSeed.dosage(),
                bomSeed.lossRate(),
                productId,
                bomSeed.flowerId()
            );
        }

        return jdbcTemplate.update(
            """
            INSERT INTO product_bom(product_id, flower_id, dosage, loss_rate, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """,
            productId,
            bomSeed.flowerId(),
            bomSeed.dosage(),
            bomSeed.lossRate()
        );
    }

    private List<Long> loadUserIds() {
        return jdbcTemplate.query(
            "SELECT id FROM user_customer ORDER BY id LIMIT 50",
            (rs, rowNum) -> rs.getLong("id")
        );
    }

    private Long createFallbackUser() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_customer(openid, name, points, created_at, updated_at) VALUES (?, ?, 0, NOW(), NOW())",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, "debug_seed_" + System.currentTimeMillis());
            ps.setString(2, "Debug Seed User");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long insertOrder(
        String orderNo,
        Long userId,
        BigDecimal totalAmount,
        BigDecimal paymentAmount,
        String status,
        String paymentChannel,
        String paymentNo,
        LocalDateTime payTime,
        LocalDateTime cancelTime,
        LocalDateTime lockExpireAt,
        String remark,
        LocalDateTime createdAt
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO customer_order(
                    order_no, user_id, total_amount, payment_amount, status,
                    payment_channel, payment_no, pay_time, cancel_time, lock_expire_at,
                    remark, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, orderNo);
            ps.setLong(2, userId);
            ps.setBigDecimal(3, totalAmount);
            ps.setBigDecimal(4, paymentAmount);
            ps.setString(5, status);
            ps.setString(6, paymentChannel);
            ps.setString(7, paymentNo);
            if (payTime == null) {
                ps.setTimestamp(8, null);
            } else {
                ps.setTimestamp(8, Timestamp.valueOf(payTime));
            }
            if (cancelTime == null) {
                ps.setTimestamp(9, null);
            } else {
                ps.setTimestamp(9, Timestamp.valueOf(cancelTime));
            }
            if (lockExpireAt == null) {
                ps.setTimestamp(10, null);
            } else {
                ps.setTimestamp(10, Timestamp.valueOf(lockExpireAt));
            }
            ps.setString(11, remark);
            ps.setTimestamp(12, Timestamp.valueOf(createdAt));
            ps.setTimestamp(13, Timestamp.valueOf(createdAt));
            return ps;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    private String generateOrderNo() {
        String time = LocalDateTime.now().format(ORDER_NO_TIME);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "DBG" + time + random;
    }

    private List<ProductSeed> buildProductSeeds() {
        List<ProductSeed> seeds = new ArrayList<>();
        seeds.add(new ProductSeed(
            "浪漫11枝红玫瑰",
            "VALENTINE",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "经典告白款，适用于纪念日和节日。",
            "/images/products/custom/rose-black-baccara.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("11.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("3.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "清新百合花束",
            "DAILY",
            new BigDecimal("5.00"),
            new BigDecimal("8.00"),
            "适合日常送礼，风格简约。",
            "/images/products/custom/rose-amour.jpg",
            List.of(
                new BomSeed(3L, new BigDecimal("6.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("2.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "康乃馨关怀款",
            "MOTHER_DAY",
            new BigDecimal("4.00"),
            new BigDecimal("8.00"),
            "母亲节与探访场景热销。",
            "/images/products/custom/rose-zhenai.jpg",
            List.of(
                new BomSeed(4L, new BigDecimal("12.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("2.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "法式香槟玫瑰花束",
            "DAILY",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "法式配色，适合约会与纪念日场景。",
            "/images/products/custom/rose-explorer.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("9.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("4.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("2.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "向日葵元气花束",
            "BIRTHDAY",
            new BigDecimal("5.00"),
            new BigDecimal("8.00"),
            "明亮活力，适合生日祝福与开业庆贺。",
            "/images/products/custom/rose-freedom.jpg",
            List.of(
                new BomSeed(3L, new BigDecimal("5.00"), new BigDecimal("0.0300")),
                new BomSeed(4L, new BigDecimal("4.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("2.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "白绿商务花束",
            "BUSINESS",
            new BigDecimal("7.00"),
            new BigDecimal("8.00"),
            "商务拜访与会议场景优选。",
            "/images/products/custom/rose-lisi.jpg",
            List.of(
                new BomSeed(3L, new BigDecimal("4.00"), new BigDecimal("0.0300")),
                new BomSeed(1L, new BigDecimal("6.00"), new BigDecimal("0.0300")),
                new BomSeed(5L, new BigDecimal("3.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "粉色告白花束",
            "VALENTINE",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "粉系浪漫组合，适合告白与周年纪念。",
            "/images/products/custom/rose-baoliandeng.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("7.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("5.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "暖心康乃馨礼盒",
            "MOTHER_DAY",
            new BigDecimal("4.00"),
            new BigDecimal("8.00"),
            "关怀主题，适合探望与节日赠礼。",
            "/images/products/custom/rose-carola.jpg",
            List.of(
                new BomSeed(4L, new BigDecimal("15.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("4.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("3.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("2.00"), new BigDecimal("0.0000"))
            )
        ));
        // Additional bouquet photos provided later are also seeded as products.
        seeds.add(new ProductSeed(
            "奶油腮红花束",
            "DAILY",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "Soft cream tone bouquet for daily gifting.",
            "/images/products/custom/bouquet-blush-cream.jpg",
            List.of(
                new BomSeed(3L, new BigDecimal("5.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("3.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "白缎蝴蝶结花束",
            "BUSINESS",
            new BigDecimal("7.00"),
            new BigDecimal("8.00"),
            "Clean white bow style suitable for formal visits.",
            "/images/products/custom/bouquet-bow-white.jpg",
            List.of(
                new BomSeed(3L, new BigDecimal("6.00"), new BigDecimal("0.0300")),
                new BomSeed(1L, new BigDecimal("4.00"), new BigDecimal("0.0300")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "花苞礼盒花束",
            "BIRTHDAY",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "Gift-box bouquet for birthday surprises.",
            "/images/products/custom/bouquet-bud-box.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("8.00"), new BigDecimal("0.0300")),
                new BomSeed(4L, new BigDecimal("3.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("3.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "丝带花苞花束",
            "VALENTINE",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "Ribbon bouquet for confession and anniversary moments.",
            "/images/products/custom/bouquet-bud-ribbon.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("9.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("4.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("2.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "白蕾丝花苞花束",
            "MOTHER_DAY",
            new BigDecimal("5.00"),
            new BigDecimal("8.00"),
            "White-lace bouquet for warm family gifting.",
            "/images/products/custom/bouquet-bud-whitelace.jpg",
            List.of(
                new BomSeed(4L, new BigDecimal("10.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("4.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "粉色花篮花束",
            "BIRTHDAY",
            new BigDecimal("7.00"),
            new BigDecimal("8.00"),
            "Pink basket arrangement with stronger ceremony feel.",
            "/images/products/custom/bouquet-pink-basket.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("7.00"), new BigDecimal("0.0300")),
                new BomSeed(3L, new BigDecimal("4.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("4.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("3.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "粉色花园花束",
            "DAILY",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "Garden style bouquet for casual celebrations.",
            "/images/products/custom/bouquet-pink-garden.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("6.00"), new BigDecimal("0.0300")),
                new BomSeed(3L, new BigDecimal("5.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("3.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "自然粉调花束",
            "DAILY",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "Natural pink tone for lightweight gifting scenes.",
            "/images/products/custom/bouquet-pink-natural.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("6.00"), new BigDecimal("0.0300")),
                new BomSeed(4L, new BigDecimal("4.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("3.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000"))
            )
        ));
        seeds.add(new ProductSeed(
            "粉玫瑰花束",
            "VALENTINE",
            new BigDecimal("6.00"),
            new BigDecimal("8.00"),
            "Pink rose mix for romantic gifting.",
            "/images/products/custom/bouquet-pink-rose.jpg",
            List.of(
                new BomSeed(1L, new BigDecimal("8.00"), new BigDecimal("0.0300")),
                new BomSeed(2L, new BigDecimal("4.00"), new BigDecimal("0.0500")),
                new BomSeed(5L, new BigDecimal("2.00"), new BigDecimal("0.0000")),
                new BomSeed(6L, new BigDecimal("1.00"), new BigDecimal("0.0000"))
            )
        ));
        return seeds;
    }

    private record ProductSeed(
        String title,
        String category,
        BigDecimal packagingFee,
        BigDecimal deliveryFee,
        String description,
        String coverImage,
        List<BomSeed> bom
    ) {
    }

    private record BomSeed(Long flowerId, BigDecimal dosage, BigDecimal lossRate) {
    }
}
