package com.flowershop.service;

import com.flowershop.dto.CancelOrderRequest;
import com.flowershop.dto.ConfirmOrderRequest;
import com.flowershop.dto.CreateOrderRequest;
import com.flowershop.dto.CreateOrderResponse;
import com.flowershop.dto.OrderDetailResponse;
import com.flowershop.dto.OrderItemResponse;
import com.flowershop.dto.OrderStockLockResponse;
import com.flowershop.dto.OrderSummaryResponse;
import com.flowershop.dto.PayOrderRequest;
import com.flowershop.dto.SimpleActionResponse;
import com.flowershop.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final int FALLBACK_LOCK_MINUTES = 30;

    private final JdbcTemplate jdbcTemplate;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final UserPointsService userPointsService;
    private final MemberBenefitsService memberBenefitsService;

    public OrderService(JdbcTemplate jdbcTemplate,
                        ProductService productService,
                        InventoryService inventoryService,
                        UserPointsService userPointsService,
                        MemberBenefitsService memberBenefitsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.userPointsService = userPointsService;
        this.memberBenefitsService = memberBenefitsService;
    }

    @PostConstruct
    public void initializeOrderSchema() {
        if (!tableExists("customer_order")) {
            return;
        }
        addColumnIfMissing(
            "delivery_fee",
            "ALTER TABLE customer_order ADD COLUMN delivery_fee DECIMAL(10,2) DEFAULT 0.00 COMMENT 'delivery fee' AFTER payment_amount"
        );
        addColumnIfMissing(
            "delivery_mode",
            "ALTER TABLE customer_order ADD COLUMN delivery_mode VARCHAR(32) DEFAULT NULL COMMENT 'delivery mode code' AFTER receiver_address"
        );
        addColumnIfMissing(
            "delivery_slot",
            "ALTER TABLE customer_order ADD COLUMN delivery_slot VARCHAR(32) DEFAULT NULL COMMENT 'delivery slot code' AFTER delivery_mode"
        );
        addColumnIfMissing(
            "tracking_company",
            "ALTER TABLE customer_order ADD COLUMN tracking_company VARCHAR(64) DEFAULT NULL COMMENT '物流公司' AFTER lock_expire_at"
        );
        addColumnIfMissing(
            "tracking_no",
            "ALTER TABLE customer_order ADD COLUMN tracking_no VARCHAR(64) DEFAULT NULL COMMENT '运单号' AFTER tracking_company"
        );
        addColumnIfMissing(
            "shipped_at",
            "ALTER TABLE customer_order ADD COLUMN shipped_at DATETIME DEFAULT NULL COMMENT '发货时间' AFTER tracking_no"
        );
        addColumnIfMissing(
            "completed_at",
            "ALTER TABLE customer_order ADD COLUMN completed_at DATETIME DEFAULT NULL COMMENT '完成时间' AFTER shipped_at"
        );
    }

    private int getLockMinutes() {
        try {
            String val = jdbcTemplate.queryForObject(
                "SELECT config_value FROM system_config WHERE config_key = 'order_lock_minutes'",
                String.class
            );
            return val != null ? Integer.parseInt(val) : FALLBACK_LOCK_MINUTES;
        } catch (Exception e) {
            return FALLBACK_LOCK_MINUTES;
        }
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        ensureUserExists(request.getUserId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockExpireAt = now.plusMinutes(getLockMinutes());
        String remark = normalizeNullableText(request.getRemark());
        String deliveryMode = normalizeNullableCode(request.getDeliveryMode());
        String deliverySlot = normalizeNullableCode(request.getDeliverySlot());

        Map<String, MerchantOrderDraft> orderDrafts = new LinkedHashMap<>();
        BigDecimal totalItemAmount = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderLine line : request.getItems()) {
            ProductService.ProductSnapshot product = productService.getProductSnapshot(line.getProductId());
            String merchantKey = resolveMerchantKey(product.merchantAccount());
            MerchantOrderDraft draft = orderDrafts.computeIfAbsent(
                merchantKey,
                key -> new MerchantOrderDraft(normalizeNullableText(product.merchantAccount()), normalizeNullableText(product.merchantName()))
            );

            BigDecimal unitPrice = productService.calculateAutoUnitPrice(product.id());
            BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(line.getQuantity()));
            draft.lines().add(new ResolvedOrderLine(product.id(), product.title(), unitPrice, line.getQuantity(), lineAmount));
            draft.itemAmount(draft.itemAmount().add(lineAmount));
            totalItemAmount = totalItemAmount.add(lineAmount);

            List<ProductService.BomDemand> demands = productService.getBomDemands(product.id());
            if (demands.isEmpty()) {
                throw new BusinessException("BOM_NOT_FOUND", "\u5546\u54c1\u7f3a\u5c11BOM\u914d\u7f6e\uff0c\u65e0\u6cd5\u6263\u51cf\u5e93\u5b58: " + product.title());
            }
            for (ProductService.BomDemand demand : demands) {
                BigDecimal needQty = demand.dosage().multiply(BigDecimal.valueOf(line.getQuantity()));
                draft.flowerDemand().merge(demand.flowerId(), needQty, BigDecimal::add);
            }
        }

        if (orderDrafts.isEmpty()) {
            throw new BusinessException("ORDER_ITEMS_EMPTY", "\u8ba2\u5355\u5546\u54c1\u4e0d\u80fd\u4e3a\u7a7a");
        }

        BigDecimal totalPackagingFee = normalizeFee(safeAmount(request.getPackagingFee()));
        BigDecimal totalDeliveryFee = normalizeFee(safeAmount(request.getDeliveryFee()));

        BigDecimal allocatedPackagingFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal allocatedDeliveryFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<CreateOrderResponse.SubOrder> subOrders = new ArrayList<>();
        int groupIndex = 0;
        int groupCount = orderDrafts.size();

        for (MerchantOrderDraft draft : orderDrafts.values()) {
            groupIndex += 1;
            String orderNo = generateOrderNo();
            Long orderId = insertOrder(orderNo, request.getUserId(), remark, lockExpireAt,
                request.getReceiverName(), request.getReceiverPhone(), request.getReceiverAddress());

            for (ResolvedOrderLine line : draft.lines()) {
                insertOrderItem(orderId, line.productId(), line.productTitle(), line.unitPrice(), line.quantity(), line.lineAmount());
            }

            boolean isLast = groupIndex == groupCount;
            BigDecimal packagingFee = allocateFee(totalPackagingFee, draft.itemAmount(), totalItemAmount, allocatedPackagingFee, isLast);
            BigDecimal deliveryFee = allocateFee(totalDeliveryFee, draft.itemAmount(), totalItemAmount, allocatedDeliveryFee, isLast);
            allocatedPackagingFee = allocatedPackagingFee.add(packagingFee);
            allocatedDeliveryFee = allocatedDeliveryFee.add(deliveryFee);

            MemberBenefitsService.OrderBenefit memberBenefit = memberBenefitsService.resolveOrderBenefit(
                request.getUserId(),
                draft.itemAmount()
            );

            BigDecimal totalAmount = memberBenefit.discountedGoodsAmount()
                .add(packagingFee)
                .add(deliveryFee)
                .setScale(2, RoundingMode.HALF_UP);

            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET total_amount = ?,
                    payment_amount = ?,
                    delivery_fee = ?,
                    delivery_mode = ?,
                    delivery_slot = ?,
                    status = 'LOCKED',
                    updated_at = NOW()
                WHERE id = ?
                """,
                totalAmount,
                totalAmount,
                deliveryFee,
                deliveryMode,
                deliverySlot,
                orderId
            );

            inventoryService.lockMaterials(orderId, orderNo, draft.flowerDemand(), lockExpireAt);
            subOrders.add(new CreateOrderResponse.SubOrder(
                orderNo,
                "LOCKED",
                totalAmount,
                lockExpireAt,
                draft.merchantAccount(),
                draft.merchantName()
            ));
        }

        CreateOrderResponse.SubOrder first = subOrders.get(0);
        BigDecimal totalAmount = subOrders.stream()
            .map(CreateOrderResponse.SubOrder::totalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        return new CreateOrderResponse(first.orderNo(), first.status(), totalAmount, first.lockExpireAt(), subOrders);
    }

    public OrderDetailResponse getOrderDetail(String orderNo) {
        OrderSnapshot snapshot = getOrderByNo(orderNo);

        List<OrderItemResponse> items = jdbcTemplate.query(
            """
            SELECT oi.product_id, oi.product_title, oi.unit_price, oi.quantity, oi.line_amount, p.cover_image
            FROM order_item oi
            LEFT JOIN product p ON p.id = oi.product_id
            WHERE oi.order_id = ?
            ORDER BY oi.id
            """,
            (rs, rowNum) -> new OrderItemResponse(
                rs.getLong("product_id"),
                rs.getString("product_title"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("line_amount"),
                rs.getString("cover_image")
            ),
            snapshot.id()
        );

        List<OrderStockLockResponse> locks = jdbcTemplate.query(
            """
            SELECT sl.flower_id, f.name AS flower_name, ROUND(SUM(sl.lock_qty), 2) AS lock_qty,
                   MAX(sl.status) AS lock_status
            FROM stock_lock sl
            JOIN flower_material f ON sl.flower_id = f.id
            WHERE sl.order_id = ?
            GROUP BY sl.flower_id, f.name
            ORDER BY sl.flower_id
            """,
            (rs, rowNum) -> new OrderStockLockResponse(
                rs.getLong("flower_id"),
                rs.getString("flower_name"),
                rs.getBigDecimal("lock_qty"),
                rs.getString("lock_status")
            ),
            snapshot.id()
        );

        return new OrderDetailResponse(
            snapshot.id(),
            snapshot.orderNo(),
            snapshot.userId(),
            snapshot.status(),
            snapshot.totalAmount(),
            snapshot.paymentAmount(),
            snapshot.deliveryFee(),
            resolveReceiverDisplayName(
                snapshot.receiverName(),
                loadUserDisplayName(snapshot.userId()),
                snapshot.receiverPhone()
            ),
            snapshot.receiverPhone(),
            snapshot.receiverAddress(),
            snapshot.deliveryMode(),
            snapshot.deliverySlot(),
            snapshot.createdAt(),
            snapshot.payTime(),
            snapshot.cancelTime(),
            snapshot.lockExpireAt(),
            snapshot.trackingCompany(),
            snapshot.trackingNo(),
            snapshot.shippedAt(),
            snapshot.remark(),
            items,
            locks
        );
    }

    public List<String> listOrderNosByUser(Long userId, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        String sql = """
            SELECT order_no
            FROM customer_order
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT %d
            """.formatted(safeLimit);

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getString("order_no"),
            userId
        );
    }

    public List<OrderSummaryResponse> listOrderDetailsByUser(Long userId, Integer limit) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        List<OrderSnapshot> orders = jdbcTemplate.query(
            """
            SELECT id, order_no, user_id, status, total_amount, payment_amount, delivery_fee,
                   receiver_name, receiver_phone, receiver_address, delivery_mode, delivery_slot,
                   created_at, pay_time, cancel_time, lock_expire_at,
                   tracking_company, tracking_no, shipped_at, remark
            FROM customer_order
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT %d
            """.formatted(safeLimit),
            this::mapOrderSnapshot,
            userId
        );

        if (orders.isEmpty()) {
            return List.of();
        }

        return buildOrderSummaries(orders);
    }

    /** Merchant side: list all orders by status. */
    public List<OrderSummaryResponse> listAllOrders(String status, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        List<OrderSnapshot> orders;

        if (status != null && !status.isBlank()) {
            orders = jdbcTemplate.query(
                """
                SELECT id, order_no, user_id, status, total_amount, payment_amount, delivery_fee,
                       receiver_name, receiver_phone, receiver_address, delivery_mode, delivery_slot,
                       created_at, pay_time, cancel_time, lock_expire_at,
                       tracking_company, tracking_no, shipped_at, remark
                FROM customer_order
                WHERE status = ?
                ORDER BY created_at DESC
                LIMIT %d
                """.formatted(safeLimit),
                this::mapOrderSnapshot,
                status.trim().toUpperCase()
            );
        } else {
            orders = jdbcTemplate.query(
                """
                SELECT id, order_no, user_id, status, total_amount, payment_amount, delivery_fee,
                       receiver_name, receiver_phone, receiver_address, delivery_mode, delivery_slot,
                       created_at, pay_time, cancel_time, lock_expire_at,
                       tracking_company, tracking_no, shipped_at, remark
                FROM customer_order
                ORDER BY created_at DESC
                LIMIT %d
                """.formatted(safeLimit),
                this::mapOrderSnapshot
            );
        }

        if (orders.isEmpty()) {
            return List.of();
        }
        return buildOrderSummaries(orders);
    }

    @Transactional
    public SimpleActionResponse payOrder(String orderNo, PayOrderRequest request) {
        OrderSnapshot order = getOrderForUpdate(orderNo);
        if ("PAID".equals(order.status()) || "COMPLETED".equals(order.status())) {
            return new SimpleActionResponse(orderNo, order.status(), "\u8ba2\u5355\u5df2\u652f\u4ed8");
        }
        if (!"LOCKED".equals(order.status())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "\u5f53\u524d\u72b6\u6001\u4e0d\u53ef\u652f\u4ed8: " + order.status());
        }

        if (order.lockExpireAt() != null && order.lockExpireAt().isBefore(LocalDateTime.now())) {
            inventoryService.releaseLockedMaterials(order.id());
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'CANCELLED',
                    cancel_time = NOW(),
                    remark = ?,
                    updated_at = NOW()
                WHERE id = ?
                """,
                appendRemark(order.remark(), "\u652f\u4ed8\u8d85\u65f6\u81ea\u52a8\u53d6\u6d88"),
                order.id()
            );
            throw new BusinessException("ORDER_EXPIRED", "\u8ba2\u5355\u8d85\u65f6\u672a\u652f\u4ed8\uff0c\u5e93\u5b58\u5df2\u91ca\u653e");
        }

        String paymentChannel = request.getPaymentChannel() == null || request.getPaymentChannel().isBlank()
            ? "WECHAT_MINI"
            : request.getPaymentChannel().trim();
        String paymentNo = request.getPaymentNo() == null || request.getPaymentNo().isBlank()
            ? "WX" + System.currentTimeMillis()
            : request.getPaymentNo().trim();

        inventoryService.confirmLockedMaterials(order.id());
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'PAID', pay_time = NOW(), payment_channel = ?, payment_no = ?, updated_at = NOW()
            WHERE id = ?
            """,
            paymentChannel,
            paymentNo,
            order.id()
        );

        userPointsService.awardPointsForAmount(order.userId(), order.paymentAmount());

        insertPaymentLog(order.id(), orderNo, paymentNo, paymentChannel, order.paymentAmount());
        return new SimpleActionResponse(orderNo, "PAID", "\u652f\u4ed8\u6210\u529f\u5e76\u5b8c\u6210\u5e93\u5b58\u6263\u51cf");
    }

    @Transactional
    public SimpleActionResponse confirmOrder(String orderNo) {
        return confirmOrder(orderNo, null);
    }

    @Transactional
    public SimpleActionResponse confirmOrder(String orderNo, ConfirmOrderRequest request) {
        OrderSnapshot order = getOrderForUpdate(orderNo);
        if ("CONFIRMED".equals(order.status())) {
            return new SimpleActionResponse(orderNo, "CONFIRMED", "\u8ba2\u5355\u5df2\u786e\u8ba4");
        }
        if (!"PAID".equals(order.status())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "\u5f53\u524d\u72b6\u6001\u4e0d\u53ef\u786e\u8ba4: " + order.status());
        }

        String trackingCompany = normalizeNullableText(request == null ? null : request.logisticsCompany());
        String trackingNo = normalizeNullableText(request == null ? null : request.trackingNo());

        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'CONFIRMED',
                tracking_company = COALESCE(?, tracking_company),
                tracking_no = COALESCE(?, tracking_no),
                shipped_at = COALESCE(shipped_at, NOW()),
                updated_at = NOW()
            WHERE id = ?
            """,
            trackingCompany,
            trackingNo,
            order.id()
        );
        return new SimpleActionResponse(orderNo, "CONFIRMED", "\u8ba2\u5355\u5df2\u786e\u8ba4\u53d1\u8d27");
    }

    @Transactional
    public SimpleActionResponse completeOrder(String orderNo) {
        OrderSnapshot order = getOrderForUpdate(orderNo);
        if ("COMPLETED".equals(order.status())) {
            return new SimpleActionResponse(orderNo, "COMPLETED", "\u8ba2\u5355\u5df2\u5b8c\u6210");
        }
        if (!"CONFIRMED".equals(order.status())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "\u5f53\u524d\u72b6\u6001\u4e0d\u53ef\u5b8c\u6210: " + order.status());
        }
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'COMPLETED', completed_at = COALESCE(completed_at, NOW()), updated_at = NOW()
            WHERE id = ?
            """,
            order.id()
        );
        return new SimpleActionResponse(orderNo, "COMPLETED", "\u8ba2\u5355\u5df2\u5b8c\u6210");
    }

    @Transactional
    public SimpleActionResponse cancelOrder(String orderNo, CancelOrderRequest request) {
        OrderSnapshot order = getOrderForUpdate(orderNo);
        String reason = request == null || request.getReason() == null || request.getReason().isBlank()
            ? "\u7528\u6237\u53d6\u6d88"
            : request.getReason().trim();

        if ("CANCELLED".equals(order.status()) || "REFUNDED".equals(order.status())) {
            return new SimpleActionResponse(orderNo, order.status(), "\u8ba2\u5355\u5df2\u53d6\u6d88\u6216\u5df2\u9000\u6b3e");
        }

        if ("LOCKED".equals(order.status()) || "CREATED".equals(order.status())) {
            inventoryService.releaseLockedMaterials(order.id());
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'CANCELLED', cancel_time = NOW(), remark = ?, updated_at = NOW()
                WHERE id = ?
                """,
                appendRemark(order.remark(), reason),
                order.id()
            );
            return new SimpleActionResponse(orderNo, "CANCELLED", "\u53d6\u6d88\u6210\u529f\uff0c\u9501\u5b9a\u5e93\u5b58\u5df2\u56de\u8865");
        }

        if ("PAID".equals(order.status()) || "CONFIRMED".equals(order.status()) || "COMPLETED".equals(order.status())) {
            inventoryService.rollbackConfirmedMaterials(order.id());
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'REFUNDED', cancel_time = NOW(), remark = ?, updated_at = NOW()
                WHERE id = ?
                """,
                appendRemark(order.remark(), reason),
                order.id()
            );
            userPointsService.rollbackPointsForAmount(order.userId(), order.paymentAmount());
            return new SimpleActionResponse(orderNo, "REFUNDED", "\u9000\u6b3e\u6210\u529f\uff0c\u5e93\u5b58\u5df2\u56de\u8865");
        }

        throw new BusinessException("ORDER_STATUS_INVALID", "\u5f53\u524d\u72b6\u6001\u4e0d\u53ef\u53d6\u6d88: " + order.status());
    }
// ===== Internal helper methods =====

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

    private Long insertOrder(String orderNo, Long userId, String remark, LocalDateTime lockExpireAt,
                             String receiverName, String receiverPhone, String receiverAddress) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO customer_order(order_no, user_id, total_amount, payment_amount, status,
                    receiver_name, receiver_phone, receiver_address, lock_expire_at, remark, created_at, updated_at)
                VALUES (?, ?, 0, 0, 'CREATED', ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, orderNo);
            ps.setLong(2, userId);
            ps.setString(3, receiverName);
            ps.setString(4, receiverPhone);
            ps.setString(5, receiverAddress);
            ps.setTimestamp(6, Timestamp.valueOf(lockExpireAt));
            ps.setString(7, remark);
            return ps;
        }, keyHolder);

        if (updated == 0 || keyHolder.getKey() == null) {
            throw new BusinessException("CREATE_ORDER_FAILED", "\u521b\u5efa\u8ba2\u5355\u5931\u8d25");
        }
        return keyHolder.getKey().longValue();
    }

    private void insertOrderItem(Long orderId, Long productId, String productTitle, BigDecimal unitPrice, Integer quantity, BigDecimal lineAmount) {
        jdbcTemplate.update(
            """
            INSERT INTO order_item(order_id, product_id, product_title, unit_price, quantity, line_amount, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            orderId,
            productId,
            productTitle,
            unitPrice,
            quantity,
            lineAmount
        );
    }

    private OrderSnapshot getOrderByNo(String orderNo) {
        List<OrderSnapshot> orders = jdbcTemplate.query(
            """
            SELECT id, order_no, user_id, status, total_amount, payment_amount, delivery_fee,
                   receiver_name, receiver_phone, receiver_address, delivery_mode, delivery_slot,
                   created_at, pay_time, cancel_time, lock_expire_at,
                   tracking_company, tracking_no, shipped_at, remark
            FROM customer_order
            WHERE order_no = ?
            """,
            this::mapOrderSnapshot,
            orderNo
        );
        if (orders.isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "\u8ba2\u5355\u4e0d\u5b58\u5728: " + orderNo);
        }
        return orders.get(0);
    }

    private OrderSnapshot getOrderForUpdate(String orderNo) {
        List<OrderSnapshot> orders = jdbcTemplate.query(
            """
            SELECT id, order_no, user_id, status, total_amount, payment_amount, delivery_fee,
                   receiver_name, receiver_phone, receiver_address, delivery_mode, delivery_slot,
                   created_at, pay_time, cancel_time, lock_expire_at,
                   tracking_company, tracking_no, shipped_at, remark
            FROM customer_order
            WHERE order_no = ?
            FOR UPDATE
            """,
            this::mapOrderSnapshot,
            orderNo
        );
        if (orders.isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "\u8ba2\u5355\u4e0d\u5b58\u5728: " + orderNo);
        }
        return orders.get(0);
    }

    private OrderSnapshot mapOrderSnapshot(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OrderSnapshot(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getLong("user_id"),
            rs.getString("status"),
            rs.getBigDecimal("total_amount"),
            rs.getBigDecimal("payment_amount"),
            rs.getBigDecimal("delivery_fee"),
            rs.getString("receiver_name"),
            rs.getString("receiver_phone"),
            rs.getString("receiver_address"),
            rs.getString("delivery_mode"),
            rs.getString("delivery_slot"),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("pay_time")),
            toLocalDateTime(rs.getTimestamp("cancel_time")),
            toLocalDateTime(rs.getTimestamp("lock_expire_at")),
            rs.getString("tracking_company"),
            rs.getString("tracking_no"),
            toLocalDateTime(rs.getTimestamp("shipped_at")),
            rs.getString("remark")
        );
    }

    private List<OrderSummaryResponse> buildOrderSummaries(List<OrderSnapshot> orders) {
        List<Long> orderIds = orders.stream().map(OrderSnapshot::id).toList();
        String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
        String itemSql = """
            SELECT oi.order_id, oi.product_id, oi.product_title, oi.unit_price, oi.quantity, oi.line_amount, p.cover_image
            FROM order_item oi
            LEFT JOIN product p ON p.id = oi.product_id
            WHERE oi.order_id IN (%s)
            ORDER BY oi.order_id DESC, oi.id ASC
            """.formatted(placeholders);

        List<OrderItemRow> itemRows = jdbcTemplate.query(
            itemSql,
            (rs, rowNum) -> new OrderItemRow(
                rs.getLong("order_id"),
                rs.getLong("product_id"),
                rs.getString("product_title"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("line_amount"),
                rs.getString("cover_image")
            ),
            orderIds.toArray()
        );

        Map<Long, List<OrderItemResponse>> itemMap = new LinkedHashMap<>();
        Map<Long, String> userDisplayNames = new LinkedHashMap<>();
        for (OrderSnapshot order : orders) {
            itemMap.put(order.id(), new ArrayList<>());
            Long userId = order.userId();
            if (userId != null && userId > 0 && !userDisplayNames.containsKey(userId)) {
                userDisplayNames.put(userId, loadUserDisplayName(userId));
            }
        }
        for (OrderItemRow row : itemRows) {
            List<OrderItemResponse> list = itemMap.computeIfAbsent(row.orderId(), key -> new ArrayList<>());
            list.add(new OrderItemResponse(
                row.productId(),
                row.productTitle(),
                row.unitPrice(),
                row.quantity(),
                row.lineAmount(),
                row.coverImage()
            ));
        }

        return orders.stream().map(order -> new OrderSummaryResponse(
            order.orderNo(),
            order.status(),
            order.totalAmount(),
            order.paymentAmount(),
            order.deliveryFee(),
            resolveReceiverDisplayName(
                order.receiverName(),
                userDisplayNames.get(order.userId()),
                order.receiverPhone()
            ),
            order.receiverPhone(),
            order.receiverAddress(),
            order.deliveryMode(),
            order.deliverySlot(),
            order.createdAt(),
            order.payTime(),
            order.cancelTime(),
            order.lockExpireAt(),
            order.trackingCompany(),
            order.trackingNo(),
            order.shippedAt(),
            order.remark(),
            itemMap.getOrDefault(order.id(), List.of())
        )).toList();
    }

    private String loadUserDisplayName(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                "SELECT name FROM user_customer WHERE id = ?",
                String.class,
                userId
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String resolveReceiverDisplayName(String receiverName, String userDisplayName, String receiverPhone) {
        String normalizedReceiverName = normalizeReceiverDisplayText(receiverName);
        if (normalizedReceiverName != null) {
            return normalizedReceiverName;
        }

        String normalizedUserDisplayName = normalizeReceiverDisplayText(userDisplayName);
        if (normalizedUserDisplayName != null) {
            return normalizedUserDisplayName;
        }

        return normalizeNullableText(receiverPhone);
    }

    private static String normalizeReceiverDisplayText(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.indexOf('?') >= 0 || normalized.indexOf('？') >= 0) {
            return null;
        }
        return normalized;
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

    private void addColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'customer_order'
              AND COLUMN_NAME = ?
            """,
            Integer.class,
            columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private static final String DEFAULT_MERCHANT_KEY = "__DEFAULT__";

    private static String resolveMerchantKey(String merchantAccount) {
        String normalized = normalizeNullableText(merchantAccount);
        return normalized == null ? DEFAULT_MERCHANT_KEY : normalized;
    }

    private static BigDecimal normalizeFee(BigDecimal value) {
        return safeAmount(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal allocateFee(
        BigDecimal totalFee,
        BigDecimal partAmount,
        BigDecimal totalAmount,
        BigDecimal allocatedSoFar,
        boolean isLast
    ) {
        BigDecimal normalizedTotalFee = normalizeFee(totalFee);
        if (normalizedTotalFee.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal normalizedAllocated = normalizeFee(allocatedSoFar);
        if (isLast) {
            BigDecimal remain = normalizedTotalFee.subtract(normalizedAllocated).setScale(2, RoundingMode.HALF_UP);
            if (remain.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return remain;
        }

        BigDecimal normalizedTotalAmount = safeAmount(totalAmount);
        if (normalizedTotalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal normalizedPart = safeAmount(partAmount);
        if (normalizedPart.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal ratio = normalizedPart.divide(normalizedTotalAmount, 10, RoundingMode.HALF_UP);
        BigDecimal fee = normalizedTotalFee.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remain = normalizedTotalFee.subtract(normalizedAllocated);
        if (fee.compareTo(remain) > 0) {
            fee = remain;
        }
        if (fee.compareTo(BigDecimal.ZERO) < 0) {
            fee = BigDecimal.ZERO;
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    private record ResolvedOrderLine(
        Long productId,
        String productTitle,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineAmount
    ) {
    }

    private static final class MerchantOrderDraft {

        private final String merchantAccount;
        private final String merchantName;
        private final List<ResolvedOrderLine> lines = new ArrayList<>();
        private final Map<Long, BigDecimal> flowerDemand = new LinkedHashMap<>();
        private BigDecimal itemAmount = BigDecimal.ZERO;

        private MerchantOrderDraft(String merchantAccount, String merchantName) {
            this.merchantAccount = merchantAccount;
            this.merchantName = merchantName;
        }

        private String merchantAccount() {
            return merchantAccount;
        }

        private String merchantName() {
            return merchantName;
        }

        private List<ResolvedOrderLine> lines() {
            return lines;
        }

        private Map<Long, BigDecimal> flowerDemand() {
            return flowerDemand;
        }

        private BigDecimal itemAmount() {
            return itemAmount;
        }

        private void itemAmount(BigDecimal value) {
            this.itemAmount = value == null ? BigDecimal.ZERO : value;
        }
    }

    private static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeNullableCode(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private static String appendRemark(String oldRemark, String appendText) {
        if (appendText == null || appendText.isBlank()) {
            return oldRemark;
        }
        if (oldRemark == null || oldRemark.isBlank()) {
            return appendText;
        }
        return oldRemark + " | " + appendText;
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void insertPaymentLog(Long orderId, String orderNo, String paymentNo, String paymentChannel, BigDecimal payAmount) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO payment_log(
                    order_id, order_no, transaction_id, payment_channel, pay_amount, result_code, notify_time, created_at
                ) VALUES (?, ?, ?, ?, ?, 'SUCCESS', NOW(), NOW())
                """,
                orderId,
                orderNo,
                paymentNo,
                paymentChannel,
                payAmount == null ? BigDecimal.ZERO : payAmount
            );
        } catch (Exception e) {
            logger.error("insert payment_log failed, orderNo={}", orderNo, e);
            throw new BusinessException("PAYMENT_LOG_WRITE_FAILED", "\u652f\u4ed8\u8bb0\u5f55\u5199\u5165\u5931\u8d25");
        }
    }

    private static String generateOrderNo() {
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "FO" + timePart + randomPart;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record OrderItemRow(
        Long orderId,
        Long productId,
        String productTitle,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineAmount,
        String coverImage
    ) {
    }

    private record OrderSnapshot(
        Long id,
        String orderNo,
        Long userId,
        String status,
        BigDecimal totalAmount,
        BigDecimal paymentAmount,
        BigDecimal deliveryFee,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String deliveryMode,
        String deliverySlot,
        LocalDateTime createdAt,
        LocalDateTime payTime,
        LocalDateTime cancelTime,
        LocalDateTime lockExpireAt,
        String trackingCompany,
        String trackingNo,
        LocalDateTime shippedAt,
        String remark
    ) {
    }
}
